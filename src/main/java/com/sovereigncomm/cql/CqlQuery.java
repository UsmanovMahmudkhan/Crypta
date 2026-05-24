package com.sovereigncomm.cql;

import java.util.List;

public class CqlQuery {
    private final List<String> fields;
    private final boolean isAllFields;
    private final String source;
    private final String filterField;
    private final String filterOperator;
    private final String filterValue;

    public CqlQuery(List<String> fields, boolean isAllFields, String source, String filterField, String filterOperator, String filterValue) {
        this.fields = fields;
        this.isAllFields = isAllFields;
        this.source = source;
        this.filterField = filterField;
        this.filterOperator = filterOperator;
        this.filterValue = filterValue;
    }

    public List<String> getFields() {
        return fields;
    }

    public boolean isAllFields() {
        return isAllFields;
    }

    public String getSource() {
        return source;
    }

    public String getFilterField() {
        return filterField;
    }

    public String getFilterOperator() {
        return filterOperator;
    }

    public String getFilterValue() {
        return filterValue;
    }

    @Override
    public String toString() {
        return "CqlQuery{" +
                "fields=" + fields +
                ", isAllFields=" + isAllFields +
                ", source='" + source + '\'' +
                ", filterField='" + filterField + '\'' +
                ", filterOperator='" + filterOperator + '\'' +
                ", filterValue='" + filterValue + '\'' +
                '}';
    }
}
