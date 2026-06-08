package com.sovereigncomm.cql;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CqlPolicyService {
    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.of(
            "audit_events", Set.of("id", "organization_id", "actor_user_id", "actor_device_id", "event_type", "target_type", "target_id", "created_at"),
            "devices", Set.of("id", "user_id", "organization_id", "platform", "trust_state", "hardware_backed", "strongbox_or_secure_enclave", "mdm_compliant", "created_at", "updated_at"),
            "rooms", Set.of("id", "organization_id", "name", "classification", "room_type", "lockdown_state", "created_at", "updated_at"));

    private final JdbcTemplate jdbcTemplate;
    private final int maxQueryChars;
    private final int maxResults;

    public CqlPolicyService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 2000, 100);
    }

    @Autowired
    public CqlPolicyService(JdbcTemplate jdbcTemplate,
                            @Value("${app.governance.cql.max-query-chars:2000}") int maxQueryChars,
                            @Value("${app.governance.cql.max-results:100}") int maxResults) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxQueryChars = Math.max(1, maxQueryChars);
        this.maxResults = Math.max(1, Math.min(maxResults, 1000));
    }

    public CqlQuery parse(String cql) {
        if (cql == null || cql.trim().isEmpty()) {
            throw new IllegalArgumentException("CQL query cannot be empty");
        }
        if (cql.length() > maxQueryChars) {
            throw new IllegalArgumentException("CQL query exceeds configured length limit");
        }

        try {
            CharStream input = CharStreams.fromString(cql);
            CQLLexer lexer = new CQLLexer(input);
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    throw new ParseCancellationException("Lexer error at line " + line + ":" + charPositionInLine + " - " + msg);
                }
            });

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CQLParser parser = new CQLParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    throw new ParseCancellationException("Parser error at line " + line + ":" + charPositionInLine + " - " + msg);
                }
            });

            CQLParser.QueryContext tree = parser.query();
            CqlExpressionVisitor visitor = new CqlExpressionVisitor();
            return (CqlQuery) visitor.visit(tree);
        } catch (ParseCancellationException e) {
            throw new IllegalArgumentException("Invalid CQL: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CQL query", e);
        }
    }

    public List<Map<String, Object>> execute(String cql, UUID organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("CQL execution requires an organization scope");
        }
        CqlQuery query = parse(cql);
        
        String tableName;
        switch (query.getSource().toUpperCase()) {
            case "AUDIT_EVENTS":
                tableName = "audit_events";
                break;
            case "DEVICES":
                tableName = "devices";
                break;
            case "ROOMS":
                tableName = "rooms";
                break;
            default:
                throw new IllegalArgumentException("Unsupported CQL source: " + query.getSource());
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        if (query.isAllFields()) {
            sql.append(String.join(", ", ALLOWED_COLUMNS.get(tableName)));
        } else {
            List<String> fields = query.getFields();
            if (fields.isEmpty()) {
                sql.append("*");
            } else {
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(sanitizeColumnName(tableName, fields.get(i)));
                }
            }
        }

        sql.append(" FROM ").append(tableName);

        List<Object> params = new ArrayList<>();
        boolean hasFilter = query.getFilterField() != null;
        if (hasFilter) {
            sql.append(" WHERE ").append(sanitizeColumnName(tableName, query.getFilterField()));
            
            String op = query.getFilterOperator().toUpperCase();
            String val = query.getFilterValue();
            
            if ("EQ".equals(op) || "=".equals(op)) {
                sql.append(" = ?");
                params.add(parseValueType(query.getFilterField(), val));
            } else if ("NEQ".equals(op) || "!=".equals(op) || "<>".equals(op)) {
                sql.append(" != ?");
                params.add(parseValueType(query.getFilterField(), val));
            } else if ("CONTAINS".equals(op)) {
                sql.append(" LIKE ?");
                params.add("%" + val + "%");
            } else {
                throw new IllegalArgumentException("Unsupported CQL operator: " + op);
            }
        }

        sql.append(hasFilter ? " AND " : " WHERE ").append("organization_id = ?");
        params.add(organizationId);
        sql.append(" LIMIT ?");
        params.add(maxResults);

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private String sanitizeColumnName(String tableName, String col) {
        if (col == null || !col.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column or field name: " + col);
        }
        String normalized = col.toLowerCase();
        if (!ALLOWED_COLUMNS.getOrDefault(tableName, Set.of()).contains(normalized)) {
            throw new IllegalArgumentException("Column is not available to CQL governance queries: " + normalized);
        }
        return normalized;
    }

    private Object parseValueType(String column, String val) {
        String colUpper = column.toUpperCase();
        if (colUpper.endsWith("ID") || "ID".equals(colUpper)) {
            try {
                return UUID.fromString(val);
            } catch (IllegalArgumentException e) {
                // Return as string if not a valid UUID
                return val;
            }
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            // Not a number, return original string
            return val;
        }
    }
}
