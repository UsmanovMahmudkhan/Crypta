package com.sovereigncomm.cql;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CqlPolicyService {

    private final JdbcTemplate jdbcTemplate;

    public CqlPolicyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CqlQuery parse(String cql) {
        if (cql == null || cql.trim().isEmpty()) {
            throw new IllegalArgumentException("CQL query cannot be empty");
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

    public List<Map<String, Object>> execute(String cql) {
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
            sql.append("*");
        } else {
            List<String> fields = query.getFields();
            if (fields.isEmpty()) {
                sql.append("*");
            } else {
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(sanitizeColumnName(fields.get(i)));
                }
            }
        }

        sql.append(" FROM ").append(tableName);

        List<Object> params = new ArrayList<>();
        if (query.getFilterField() != null) {
            sql.append(" WHERE ").append(sanitizeColumnName(query.getFilterField()));
            
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

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private String sanitizeColumnName(String col) {
        if (col == null || !col.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column or field name: " + col);
        }
        return col.toLowerCase();
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
