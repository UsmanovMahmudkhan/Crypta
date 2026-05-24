package com.sovereigncomm.cql;

import java.util.ArrayList;
import java.util.List;

public class CqlExpressionVisitor extends CQLBaseVisitor<Object> {
    private List<String> fields = new ArrayList<>();
    private boolean isAllFields = false;
    private String source;
    private String filterField;
    private String filterOperator;
    private String filterValue;

    @Override
    public CqlQuery visitQuery(CQLParser.QueryContext ctx) {
        visit(ctx.selectClause());
        visit(ctx.fromClause());
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return new CqlQuery(fields, isAllFields, source, filterField, filterOperator, filterValue);
    }

    @Override
    public Object visitSelectClause(CQLParser.SelectClauseContext ctx) {
        return visit(ctx.fields());
    }

    @Override
    public Object visitFields(CQLParser.FieldsContext ctx) {
        if (ctx.ALL() != null) {
            isAllFields = true;
        } else {
            for (CQLParser.FieldContext fieldCtx : ctx.field()) {
                fields.add((String) visitField(fieldCtx));
            }
        }
        return null;
    }

    @Override
    public String visitField(CQLParser.FieldContext ctx) {
        return ctx.IDENTIFIER().getText();
    }

    @Override
    public Object visitFromClause(CQLParser.FromClauseContext ctx) {
        return visit(ctx.source());
    }

    @Override
    public Object visitSource(CQLParser.SourceContext ctx) {
        source = ctx.getText().toUpperCase();
        return null;
    }

    @Override
    public Object visitWhereClause(CQLParser.WhereClauseContext ctx) {
        return visit(ctx.condition());
    }

    @Override
    public Object visitCondition(CQLParser.ConditionContext ctx) {
        filterField = (String) visitField(ctx.field());
        filterOperator = ctx.operator().getText().toUpperCase();
        filterValue = (String) visitValue(ctx.value());
        return null;
    }

    @Override
    public String visitValue(CQLParser.ValueContext ctx) {
        String val = ctx.getText();
        if (ctx.STRING() != null) {
            // Strip the enclosing single quotes and unescape doubled single quotes
            val = val.substring(1, val.length() - 1).replace("''", "'");
        }
        return val;
    }
}
