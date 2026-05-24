package com.sovereigncomm.smalltalk;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight Smalltalk-inspired dynamic rule evaluation engine.
 * Supports unary messages (e.g., receiver key), binary messages (=, !=, >, <, contains),
 * blocks [ :param | expr ], and keyword messages (ifTrue: [ expr ]).
 */
public class SmalltalkEngine {

    public Object evaluate(String script, Map<String, Object> context) {
        if (script == null || script.trim().isEmpty()) {
            return null;
        }

        String cleaned = script.trim();
        
        // Handle Block: [ :x | ... ]
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            return new SmalltalkBlock(cleaned, this, context);
        }

        return evaluateExpression(cleaned, context);
    }

    protected Object evaluateExpression(String expr, Map<String, Object> context) {
        expr = expr.trim();
        if (expr.equals("true")) return Boolean.TRUE;
        if (expr.equals("false")) return Boolean.FALSE;
        if (expr.equals("nil")) return null;

        // String literals: 'text'
        if (expr.startsWith("'") && expr.endsWith("'")) {
            return expr.substring(1, expr.length() - 1).replace("''", "'");
        }

        // Numeric literals
        if (expr.matches("^-?\\d+$")) {
            return Integer.parseInt(expr);
        }

        // Parentheses evaluation: (expr)
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluateExpression(expr.substring(1, expr.length() - 1), context);
        }

        // Check if it's a binary operation: left op right
        // Operators supported: =, !=, <>, >, <, >=, <=, contains
        String[] binaryOps = {"!=", "<>", ">=", "<=", "=", ">", "<", "contains"};
        for (String op : binaryOps) {
            int opIdx = findOperatorIndex(expr, op);
            if (opIdx != -1) {
                String leftStr = expr.substring(0, opIdx).trim();
                String rightStr = expr.substring(opIdx + op.length()).trim();
                Object leftVal = evaluateExpression(leftStr, context);
                Object rightVal = evaluateExpression(rightStr, context);
                return evaluateBinary(leftVal, op, rightVal);
            }
        }

        // Unary message send or variable lookup
        // e.g. "event type" -> receiver "event", unary message "type"
        String[] parts = expr.split("\\s+");
        if (parts.length == 1) {
            // Variable lookup
            String varName = parts[0];
            if (context.containsKey(varName)) {
                return context.get(varName);
            }
            return varName; // Fallback to raw string
        } else if (parts.length == 2) {
            // Unary message send: receiver message
            Object receiver = evaluateExpression(parts[0], context);
            String message = parts[1];
            return sendUnaryMessage(receiver, message);
        }

        throw new IllegalArgumentException("Unsupported Smalltalk expression: " + expr);
    }

    private int findOperatorIndex(String expr, String op) {
        // Find operator outside of string literals '...' and brackets [...] or parentheses (...)
        int depth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '\'') {
                if (i == 0 || expr.charAt(i - 1) != '\\') {
                    inString = !inString;
                }
            }
            if (inString) continue;

            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;

            if (depth == 0 && bracketDepth == 0) {
                if (expr.startsWith(op, i)) {
                    // Check word boundary for word-based operators like "contains"
                    if (op.equals("contains")) {
                        if (i > 0 && Character.isLetterOrDigit(expr.charAt(i - 1))) continue;
                        if (i + op.length() < expr.length() && Character.isLetterOrDigit(expr.charAt(i + op.length()))) continue;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    private Object evaluateBinary(Object left, String op, Object right) {
        if (left == null || right == null) {
            if ("=".equals(op)) return left == right;
            if ("!=".equals(op) || "<>".equals(op)) return left != right;
            return false;
        }

        if (left instanceof Number && right instanceof Number) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            switch (op) {
                case "=": return l == r;
                case "!=":
                case "<>": return l != r;
                case ">": return l > r;
                case "<": return l < r;
                case ">=": return l >= r;
                case "<=": return l <= r;
            }
        }

        String lStr = String.valueOf(left);
        String rStr = String.valueOf(right);

        switch (op) {
            case "=": return lStr.equals(rStr);
            case "!=":
            case "<>": return !lStr.equals(rStr);
            case "contains": return lStr.contains(rStr);
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private Object sendUnaryMessage(Object receiver, String message) {
        if (receiver == null) return null;
        
        if (receiver instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) receiver;
            if (map.containsKey(message)) {
                return map.get(message);
            }
        }

        // Try reflection
        try {
            String getterName = "get" + Character.toUpperCase(message.charAt(0)) + message.substring(1);
            return receiver.getClass().getMethod(getterName).invoke(receiver);
        } catch (Exception e) {
            try {
                return receiver.getClass().getMethod(message).invoke(receiver);
            } catch (Exception ex) {
                // Ignore and fallback
            }
        }

        return null;
    }

    public static class SmalltalkBlock {
        private final String blockText;
        private final SmalltalkEngine engine;
        private final Map<String, Object> parentContext;
        private String parameterName;
        private String bodyText;

        public SmalltalkBlock(String blockText, SmalltalkEngine engine, Map<String, Object> parentContext) {
            this.blockText = blockText;
            this.engine = engine;
            this.parentContext = parentContext;
            parseBlock();
        }

        private void parseBlock() {
            String inner = blockText.substring(1, blockText.length() - 1).trim();
            if (inner.startsWith(":")) {
                int pipe = inner.indexOf('|');
                if (pipe != -1) {
                    this.parameterName = inner.substring(1, pipe).trim();
                    this.bodyText = inner.substring(pipe + 1).trim();
                    return;
                }
            }
            this.bodyText = inner;
        }

        public Object value(Object arg) {
            Map<String, Object> newContext = new HashMap<>(parentContext);
            if (parameterName != null) {
                newContext.put(parameterName, arg);
            }
            return engine.evaluateExpression(bodyText, newContext);
        }

        @Override
        public String toString() {
            return blockText;
        }
    }
}
