package com.agentworkflow.engine;

import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionEngine {
    
    // 正则表达式用于匹配表达式中的标记
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\s*(=>|<=|==|!=|>|<|&&|\\|\\||!|\\(|\\)|contains|notContains|true|false|\\d+\\.\\d+|\\d+|\\w+|'[^']*')\\s*",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 执行条件表达式
     * @param expression 条件表达式字符串
     * @param variables 全局变量映射
     * @return 表达式执行结果
     */
    public boolean evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().equals("true")) {
            return true;
        }
        if (expression.trim().equals("false")) {
            return false;
        }
        
        // 替换变量
        String processedExpression = replaceVariables(expression, variables);
        
        // 解析并执行表达式
        return parseExpression(processedExpression);
    }
    
    /**
     * 替换表达式中的变量
     */
    private String replaceVariables(String expression, Map<String, Object> variables) {
        if (variables == null) {
            return expression;
        }
        
        // 匹配 ${variableName} 格式的变量
        Pattern varPattern = Pattern.compile("\\$\\{([\\w]+)\\}");
        Matcher matcher = varPattern.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            if (value != null) {
                // 如果是字符串，添加引号
                if (value instanceof String) {
                    matcher.appendReplacement(sb, "'" + escapeString(value.toString()) + "'");
                } else {
                    matcher.appendReplacement(sb, value.toString());
                }
            } else {
                // 变量不存在，替换为空字符串
                matcher.appendReplacement(sb, "''");
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 转义字符串中的特殊字符
     */
    private String escapeString(String str) {
        return str.replace("'", "\\'");
    }
    
    /**
     * 解析表达式
     */
    private boolean parseExpression(String expression) {
        Stack<Object> values = new Stack<>();
        Stack<String> operators = new Stack<>();
        
        Matcher matcher = TOKEN_PATTERN.matcher(expression);
        
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null || token.isEmpty()) {
                continue;
            }
            
            // 数字或字符串常量
            if (token.matches("\\d+\\.\\d+")) {
                values.push(Double.parseDouble(token));
            } else if (token.matches("\\d+")) {
                values.push(Long.parseLong(token));
            } else if (token.startsWith("'") && token.endsWith("'")) {
                values.push(token.substring(1, token.length() - 1));
            } 
            // 布尔常量
            else if (token.equalsIgnoreCase("true")) {
                values.push(true);
            } else if (token.equalsIgnoreCase("false")) {
                values.push(false);
            } 
            // 左括号
            else if (token.equals("(")) {
                operators.push(token);
            } 
            // 右括号
            else if (token.equals(")")) {
                while (!operators.peek().equals("(")) {
                    applyOperator(values, operators);
                }
                operators.pop(); // 弹出左括号
            } 
            // 运算符
            else {
                while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(token)) {
                    applyOperator(values, operators);
                }
                operators.push(token);
            }
        }
        
        // 处理剩余的运算符
        while (!operators.isEmpty()) {
            applyOperator(values, operators);
        }
        
        if (values.size() != 1) {
            throw new IllegalArgumentException("Invalid expression: " + expression);
        }
        
        Object result = values.pop();
        if (!(result instanceof Boolean)) {
            throw new IllegalArgumentException("Expression must evaluate to boolean: " + expression);
        }
        
        return (Boolean) result;
    }
    
    /**
     * 获取运算符优先级
     */
    private int precedence(String operator) {
        switch (operator) {
            case "!":
                return 4;
            case "*":
            case "/":
                return 3;
            case "+":
            case "-":
                return 2;
            case "==":
            case "!=":
            case ">":
            case "<":
            case ">=":
            case "<=":
            case "contains":
            case "notContains":
                return 1;
            case "&&":
                return 0;
            case "||":
                return -1;
            default:
                return -2;
        }
    }
    
    /**
     * 应用运算符
     */
    private void applyOperator(Stack<Object> values, Stack<String> operators) {
        String operator = operators.pop();
        
        if (operator.equals("!")) {
            // 一元运算符
            boolean value = (Boolean) values.pop();
            values.push(!value);
            return;
        }
        
        // 二元运算符
        Object right = values.pop();
        Object left = values.pop();
        
        boolean result = false;
        
        switch (operator) {
            // 数值比较
            case "==":
                result = compareEqual(left, right);
                break;
            case "!=":
                result = !compareEqual(left, right);
                break;
            case ">":
                result = compareGreater(left, right);
                break;
            case "<":
                result = compareLess(left, right);
                break;
            case ">=":
                result = compareGreaterOrEqual(left, right);
                break;
            case "<=":
                result = compareLessOrEqual(left, right);
                break;
            
            // 字符串操作
            case "contains":
                result = left.toString().contains(right.toString());
                break;
            case "notContains":
                result = !left.toString().contains(right.toString());
                break;
            
            // 逻辑运算
            case "&&":
                result = (Boolean) left && (Boolean) right;
                break;
            case "||":
                result = (Boolean) left || (Boolean) right;
                break;
            
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
        
        values.push(result);
    }
    
    /**
     * 比较相等
     */
    private boolean compareEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        
        // 转换为同一类型进行比较
        if (left instanceof Number && right instanceof Number) {
            double leftVal = ((Number) left).doubleValue();
            double rightVal = ((Number) right).doubleValue();
            return leftVal == rightVal;
        }
        
        return left.equals(right);
    }
    
    /**
     * 比较大于
     */
    private boolean compareGreater(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            double leftVal = ((Number) left).doubleValue();
            double rightVal = ((Number) right).doubleValue();
            return leftVal > rightVal;
        }
        throw new IllegalArgumentException("Greater than comparison requires numeric values");
    }
    
    /**
     * 比较小于
     */
    private boolean compareLess(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            double leftVal = ((Number) left).doubleValue();
            double rightVal = ((Number) right).doubleValue();
            return leftVal < rightVal;
        }
        throw new IllegalArgumentException("Less than comparison requires numeric values");
    }
    
    /**
     * 比较大于等于
     */
    private boolean compareGreaterOrEqual(Object left, Object right) {
        return !compareLess(left, right);
    }
    
    /**
     * 比较小于等于
     */
    private boolean compareLessOrEqual(Object left, Object right) {
        return !compareGreater(left, right);
    }
}
