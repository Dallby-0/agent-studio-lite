package com.agentworkflow.engine;

import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式解析器
 * 用于分支节点条件处解析表达式
 * 
 * 支持功能：
 * 1. ${变量名} 引用变量
 * 2. 直接输入数字（整数或浮点数）
 * 3. '字符串内容' 输入字符串
 * 4. ! + - * / 数值运算
 * 5. == != > < >= <= 比较运算
 * 6. () 括号决定优先级
 * 7. && || 与或运算
 * 8. cat(A,B) len(A) contains(A,B) 字符串操作
 */
public class ExpressionParser {
    
    // 用于匹配表达式的标记
    // 注意：运算符顺序很重要，长的运算符要先匹配（如 >= 要在 > 之前）
    // 字符串字面量在 nextToken() 中单独处理
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\s*(>=|<=|==|!=|>|<|&&|\\|\\||!|\\+|-|\\*|/|\\(|\\)|,|cat|len|contains|\\d+\\.\\d+|\\d+|\\w+)\\s*"
    );
    
    private String expression;
    private Map<String, Object> variables;
    private int pos;
    private String currentToken;
    
    /**
     * 解析表达式并返回结果
     * @param expression 表达式字符串
     * @param variables 变量映射
     * @return 表达式计算结果（可能是数字、字符串或布尔值）
     */
    public Object parse(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        this.expression = expression.trim();
        this.variables = variables;
        this.pos = 0;
        
        // 先替换变量引用
        this.expression = replaceVariables(this.expression, variables);
        
        // 重置位置并开始解析
        this.pos = 0;
        this.currentToken = null;
        
        // 调试：输出表达式和初始位置
        System.out.println("    [ExpressionParser] 开始解析表达式: \"" + this.expression + "\"");
        System.out.println("    [ExpressionParser] 表达式长度: " + this.expression.length());
        
        Object result = parseExpression();
        
        // 调试：输出解析后的位置
        System.out.println("    [ExpressionParser] 解析完成，当前位置: " + pos + ", 表达式长度: " + this.expression.length());
        
        if (pos < this.expression.length()) {
            String remaining = this.expression.substring(pos);
            System.out.println("    [ExpressionParser] 错误：表达式解析未完成，剩余内容: \"" + remaining + "\"");
            throw new IllegalArgumentException("表达式解析未完成，剩余内容: " + remaining);
        }
        
        System.out.println("    [ExpressionParser] 解析成功，结果: " + result + " (类型: " + (result != null ? result.getClass().getSimpleName() : "null") + ")");
        return result;
    }
    
    /**
     * 替换表达式中的变量引用 ${变量名}
     */
    private String replaceVariables(String expression, Map<String, Object> variables) {
        if (variables == null) {
            return expression;
        }
        
        // 修改正则以支持中文变量名，匹配 ${...} 中的任意非右括号字符
        Pattern varPattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = varPattern.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = variables.get(varName);
            if (value != null) {
                // 根据值的类型进行替换
                if (value instanceof String) {
                    // 字符串值用单引号包裹
                    matcher.appendReplacement(sb, "'" + escapeString(value.toString()) + "'");
                } else if (value instanceof Number) {
                    // 数字直接替换
                    matcher.appendReplacement(sb, value.toString());
                } else if (value instanceof Boolean) {
                    // 布尔值转换为数字
                    matcher.appendReplacement(sb, ((Boolean) value) ? "1" : "0");
                } else {
                    // 其他类型转为字符串
                    matcher.appendReplacement(sb, "'" + escapeString(value.toString()) + "'");
                }
            } else {
                // 变量不存在，替换为0
                matcher.appendReplacement(sb, "0");
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
     * 获取下一个标记
     */
    private String nextToken() {
        if (currentToken != null) {
            String token = currentToken;
            currentToken = null;
            return token;
        }
        return readRawToken();
    }

    /**
     * 从字符串读取下一个标记（内部使用）
     */
    private String readRawToken() {
        if (pos >= expression.length()) {
            return null;
        }
        
        // 跳过空白字符
        int startPos = pos;
        while (pos < expression.length() && Character.isWhitespace(expression.charAt(pos))) {
            pos++;
        }
        
        if (pos >= expression.length()) {
            return null;
        }
        
        // 特殊处理字符串字面量，支持转义的引号
        if (expression.charAt(pos) == '\'') {
            return parseStringLiteral();
        }
        
        String remaining = expression.substring(pos);
        Matcher matcher = TOKEN_PATTERN.matcher(remaining);
        if (matcher.lookingAt()) {
            String token = matcher.group(1);
            int tokenEnd = matcher.end();
            pos += tokenEnd;
            System.out.println("    [readRawToken] 位置 " + startPos + " -> " + pos + ", token: \"" + token + "\", 剩余: \"" + (pos < expression.length() ? expression.substring(pos) : "") + "\"");
            return token;
        }
        
        throw new IllegalArgumentException("无法解析的标记，位置: " + pos + ", 内容: " + expression.substring(pos));
    }
    
    /**
     * 解析字符串字面量，支持转义的引号
     */
    private String parseStringLiteral() {
        int start = pos;
        pos++; // 跳过开始的引号
        
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        
        while (pos < expression.length()) {
            char ch = expression.charAt(pos);
            if (ch == '\\' && pos + 1 < expression.length()) {
                // 转义字符
                char next = expression.charAt(pos + 1);
                if (next == '\'' || next == '\\') {
                    sb.append(ch);
                    sb.append(next);
                    pos += 2;
                } else {
                    sb.append(ch);
                    pos++;
                }
            } else if (ch == '\'') {
                // 结束引号
                sb.append(ch);
                pos++;
                return sb.toString();
            } else {
                sb.append(ch);
                pos++;
            }
        }
        
        throw new IllegalArgumentException("字符串字面量未闭合，位置: " + start);
    }
    
    /**
     * 查看当前标记但不移动位置
     */
    private String peekToken() {
        if (currentToken != null) {
            return currentToken;
        }
        
        currentToken = readRawToken();
        return currentToken;
    }
    
    /**
     * 解析表达式（最低优先级）
     */
    private Object parseExpression() {
        return parseOr();
    }
    
    /**
     * 解析或运算 ||
     */
    private Object parseOr() {
        Object left = parseAnd();
        
        while (peekToken() != null && peekToken().equals("||")) {
            nextToken(); // 消费 ||
            Object right = parseAnd();
            left = applyOr(left, right);
        }
        
        return left;
    }
    
    /**
     * 解析与运算 &&
     */
    private Object parseAnd() {
        Object left = parseComparison();
        
        while (peekToken() != null && peekToken().equals("&&")) {
            nextToken(); // 消费 &&
            Object right = parseComparison();
            left = applyAnd(left, right);
        }
        
        return left;
    }
    
    /**
     * 解析比较运算 == != > < >= <=
     */
    private Object parseComparison() {
        Object left = parseAdditive();
        
        String token = peekToken();
        while (token != null && (token.equals("==") || token.equals("!=") || 
                                 token.equals(">") || token.equals("<") || 
                                 token.equals(">=") || token.equals("<="))) {
            nextToken(); // 消费比较运算符
            Object right = parseAdditive();
            left = applyComparison(token, left, right);
            token = peekToken();
        }
        
        return left;
    }
    
    /**
     * 解析加减运算 + -
     */
    private Object parseAdditive() {
        Object left = parseMultiplicative();
        
        String token = peekToken();
        System.out.println("    [parseAdditive] 左侧结果: " + left + ", 下一个token: " + token + ", 位置: " + pos);
        while (token != null && (token.equals("+") || token.equals("-"))) {
            System.out.println("    [parseAdditive] 找到运算符: " + token);
            nextToken(); // 消费 + 或 -
            Object right = parseMultiplicative();
            System.out.println("    [parseAdditive] 右侧结果: " + right);
            if (token.equals("+")) {
                left = applyAdd(left, right);
            } else {
                left = applySubtract(left, right);
            }
            System.out.println("    [parseAdditive] 运算结果: " + left);
            token = peekToken();
            System.out.println("    [parseAdditive] 下一个token: " + token);
        }
        
        return left;
    }
    
    /**
     * 解析乘除运算 * /
     */
    private Object parseMultiplicative() {
        Object left = parseUnary();
        
        String token = peekToken();
        while (token != null && (token.equals("*") || token.equals("/"))) {
            nextToken(); // 消费 * 或 /
            Object right = parseUnary();
            if (token.equals("*")) {
                left = applyMultiply(left, right);
            } else {
                left = applyDivide(left, right);
            }
            token = peekToken();
        }
        
        return left;
    }
    
    /**
     * 解析一元运算 ! 和负号 -
     */
    private Object parseUnary() {
        String token = peekToken();
        
        if (token != null && token.equals("!")) {
            nextToken(); // 消费 !
            Object operand = parseUnary();
            return applyNot(operand);
        }
        
        if (token != null && token.equals("-")) {
            nextToken(); // 消费 -
            Object operand = parseUnary();
            return applyNegate(operand);
        }
        
        return parsePrimary();
    }
    
    /**
     * 解析基本元素：数字、字符串、函数调用、括号表达式
     */
    private Object parsePrimary() {
        String token = nextToken();
        
        if (token == null) {
            throw new IllegalArgumentException("表达式不完整");
        }
        
        // 数字（整数或浮点数）
        if (token.matches("\\d+\\.\\d+")) {
            return Double.parseDouble(token);
        } else if (token.matches("\\d+")) {
            return Long.parseLong(token);
        }
        
        // 字符串字面量
        if (token.startsWith("'") && token.endsWith("'")) {
            String content = token.substring(1, token.length() - 1);
            // 处理转义字符：\\' -> ' 和 \\\\ -> \
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char ch = content.charAt(i);
                if (ch == '\\' && i + 1 < content.length()) {
                    char next = content.charAt(i + 1);
                    if (next == '\'' || next == '\\') {
                        sb.append(next);
                        i++; // 跳过下一个字符
                    } else {
                        sb.append(ch);
                    }
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        
        // 函数调用
        if (token.equals("cat") || token.equals("len") || token.equals("contains")) {
            return parseFunction(token);
        }
        
        // 左括号
        if (token.equals("(")) {
            Object result = parseExpression();
            String next = nextToken();
            if (next == null || !next.equals(")")) {
                throw new IllegalArgumentException("缺少右括号");
            }
            return result;
        }
        
        throw new IllegalArgumentException("无法识别的标记: " + token);
    }
    
    /**
     * 解析函数调用
     */
    private Object parseFunction(String functionName) {
        String token = nextToken();
        if (token == null || !token.equals("(")) {
            throw new IllegalArgumentException("函数调用缺少左括号: " + functionName);
        }
        
        if (functionName.equals("len")) {
            // len(A) 只需要一个参数
            Object arg = parseExpression();
            token = nextToken();
            if (token == null || !token.equals(")")) {
                throw new IllegalArgumentException("函数 len 缺少右括号");
            }
            return applyLen(arg);
        } else if (functionName.equals("cat")) {
            // cat(A,B) 需要两个参数
            Object arg1 = parseExpression();
            token = nextToken();
            if (token == null || !token.equals(",")) {
                throw new IllegalArgumentException("函数 cat 缺少逗号");
            }
            Object arg2 = parseExpression();
            token = nextToken();
            if (token == null || !token.equals(")")) {
                throw new IllegalArgumentException("函数 cat 缺少右括号");
            }
            return applyCat(arg1, arg2);
        } else if (functionName.equals("contains")) {
            // contains(A,B) 需要两个参数
            Object arg1 = parseExpression();
            token = nextToken();
            if (token == null || !token.equals(",")) {
                throw new IllegalArgumentException("函数 contains 缺少逗号");
            }
            Object arg2 = parseExpression();
            token = nextToken();
            if (token == null || !token.equals(")")) {
                throw new IllegalArgumentException("函数 contains 缺少右括号");
            }
            return applyContains(arg1, arg2);
        }
        
        throw new IllegalArgumentException("未知的函数: " + functionName);
    }
    
    /**
     * 将值转换为数值（用于数值运算）
     * 规则：
     * 1. 是合法整型则转化为整型
     * 2. 是合法浮点则转化为浮点
     * 3. 如果是'true'则转化为1
     * 4. 其他情况一律转化为0
     */
    private Number convertToNumber(Object value) {
        if (value == null) {
            return 0L;
        }
        
        if (value instanceof Number) {
            return (Number) value;
        }
        
        if (value instanceof String) {
            String str = (String) value;
            
            // 检查是否是 'true'
            if (str.equalsIgnoreCase("true")) {
                return 1L;
            }
            
            // 尝试解析为整数
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                // 不是整数，继续尝试浮点数
            }
            
            // 尝试解析为浮点数
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                // 不是浮点数，返回0
                return 0L;
            }
        }
        
        // 其他类型转为0
        return 0L;
    }
    
    /**
     * 将值转换为整数（用于逻辑运算）
     */
    private long convertToInt(Object value) {
        Number num = convertToNumber(value);
        return num.longValue();
    }
    
    /**
     * 应用非运算 !
     */
    private Object applyNot(Object operand) {
        long intValue = convertToInt(operand);
        return (intValue == 0) ? 1L : 0L;
    }
    
    /**
     * 应用取负运算 -
     */
    private Object applyNegate(Object operand) {
        Number num = convertToNumber(operand);
        if (num instanceof Double || num.doubleValue() != num.longValue()) {
            return -num.doubleValue();
        }
        return -num.longValue();
    }
    
    /**
     * 应用加法运算 +
     */
    private Object applyAdd(Object left, Object right) {
        Number leftNum = convertToNumber(left);
        Number rightNum = convertToNumber(right);
        
        // 如果任一操作数是浮点数，返回浮点数
        if (leftNum instanceof Double || rightNum instanceof Double || 
            leftNum.doubleValue() != leftNum.longValue() || 
            rightNum.doubleValue() != rightNum.longValue()) {
            return leftNum.doubleValue() + rightNum.doubleValue();
        }
        
        return leftNum.longValue() + rightNum.longValue();
    }
    
    /**
     * 应用减法运算 -
     */
    private Object applySubtract(Object left, Object right) {
        Number leftNum = convertToNumber(left);
        Number rightNum = convertToNumber(right);
        
        // 如果任一操作数是浮点数，返回浮点数
        if (leftNum instanceof Double || rightNum instanceof Double || 
            leftNum.doubleValue() != leftNum.longValue() || 
            rightNum.doubleValue() != rightNum.longValue()) {
            return leftNum.doubleValue() - rightNum.doubleValue();
        }
        
        return leftNum.longValue() - rightNum.longValue();
    }
    
    /**
     * 应用乘法运算 *
     */
    private Object applyMultiply(Object left, Object right) {
        Number leftNum = convertToNumber(left);
        Number rightNum = convertToNumber(right);
        
        // 如果任一操作数是浮点数，返回浮点数
        if (leftNum instanceof Double || rightNum instanceof Double || 
            leftNum.doubleValue() != leftNum.longValue() || 
            rightNum.doubleValue() != rightNum.longValue()) {
            return leftNum.doubleValue() * rightNum.doubleValue();
        }
        
        return leftNum.longValue() * rightNum.longValue();
    }
    
    /**
     * 应用除法运算 /
     */
    private Object applyDivide(Object left, Object right) {
        Number leftNum = convertToNumber(left);
        Number rightNum = convertToNumber(right);
        
        // 除法总是返回浮点数
        double rightValue = rightNum.doubleValue();
        if (rightValue == 0.0) {
            throw new ArithmeticException("除以零");
        }
        
        return leftNum.doubleValue() / rightValue;
    }
    
    /**
     * 应用比较运算
     * 对于不同的数值类型，转化为浮点比较
     * 对于数值和字符串类型，转化为字符串比较
     * 返回值1或0
     */
    private Object applyComparison(String operator, Object left, Object right) {
        boolean result = false;
        
        // 如果都是数值类型，进行浮点比较
        if (left instanceof Number && right instanceof Number) {
            double leftVal = ((Number) left).doubleValue();
            double rightVal = ((Number) right).doubleValue();
            
            switch (operator) {
                case "==":
                    result = leftVal == rightVal;
                    break;
                case "!=":
                    result = leftVal != rightVal;
                    break;
                case ">":
                    result = leftVal > rightVal;
                    break;
                case "<":
                    result = leftVal < rightVal;
                    break;
                case ">=":
                    result = leftVal >= rightVal;
                    break;
                case "<=":
                    result = leftVal <= rightVal;
                    break;
            }
        } else {
            // 数值和字符串类型，转化为字符串比较
            String leftStr = left != null ? left.toString() : "";
            String rightStr = right != null ? right.toString() : "";
            
            int cmp = leftStr.compareTo(rightStr);
            
            switch (operator) {
                case "==":
                    result = cmp == 0;
                    break;
                case "!=":
                    result = cmp != 0;
                    break;
                case ">":
                    result = cmp > 0;
                    break;
                case "<":
                    result = cmp < 0;
                    break;
                case ">=":
                    result = cmp >= 0;
                    break;
                case "<=":
                    result = cmp <= 0;
                    break;
            }
        }
        
        return result ? 1L : 0L;
    }
    
    /**
     * 应用与运算 &&
     * 规则与C++相似，只判断0和非0而返回1
     */
    private Object applyAnd(Object left, Object right) {
        long leftInt = convertToInt(left);
        long rightInt = convertToInt(right);
        return (leftInt != 0 && rightInt != 0) ? 1L : 0L;
    }
    
    /**
     * 应用或运算 ||
     * 规则与C++相似，只判断0和非0而返回1
     */
    private Object applyOr(Object left, Object right) {
        long leftInt = convertToInt(left);
        long rightInt = convertToInt(right);
        return (leftInt != 0 || rightInt != 0) ? 1L : 0L;
    }
    
    /**
     * 应用字符串连接函数 cat(A,B)
     */
    private Object applyCat(Object arg1, Object arg2) {
        String str1 = arg1 != null ? arg1.toString() : "";
        String str2 = arg2 != null ? arg2.toString() : "";
        return str1 + str2;
    }
    
    /**
     * 应用字符串长度函数 len(A)
     */
    private Object applyLen(Object arg) {
        String str = arg != null ? arg.toString() : "";
        return (long) str.length();
    }
    
    /**
     * 应用字符串包含函数 contains(A,B)
     * 返回1或0，1代表A中可以找到B
     */
    private Object applyContains(Object arg1, Object arg2) {
        String str1 = arg1 != null ? arg1.toString() : "";
        String str2 = arg2 != null ? arg2.toString() : "";
        return str1.contains(str2) ? 1L : 0L;
    }
}

