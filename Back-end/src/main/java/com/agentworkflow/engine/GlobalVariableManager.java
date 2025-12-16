package com.agentworkflow.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.agentworkflow.entity.GlobalVariable;

public class GlobalVariableManager {
    
    private Map<String, Object> variables = new HashMap<>();
    
    /**
     * 初始化全局变量
     * @param globalVariables 全局变量配置列表
     * @param inputParams 输入参数
     */
    public void initialize(List<GlobalVariable> globalVariables, Map<String, Object> inputParams) {
        variables.clear();
        
        // 初始化全局变量
        if (globalVariables != null) {
            for (GlobalVariable var : globalVariables) {
                Object value = parseValue(var.getType(), var.getInitialValue());
                variables.put(var.getName(), value);
            }
        }
        
        // 合并输入参数
        if (inputParams != null) {
            for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * 解析变量值
     * @param type 变量类型
     * @param valueStr 值字符串
     * @return 解析后的值对象
     */
    public Object parseValue(String type, String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            return getDefaultValue(type);
        }
        
        switch (type.toLowerCase()) {
            case "string":
                return valueStr;
            case "integer":
                return Long.parseLong(valueStr);
            case "double":
                return Double.parseDouble(valueStr);
            default:
                throw new IllegalArgumentException("Unsupported variable type: " + type);
        }
    }
    
    /**
     * 获取类型默认值
     * @param type 变量类型
     * @return 默认值
     */
    private Object getDefaultValue(String type) {
        switch (type.toLowerCase()) {
            case "string":
                return "";
            case "integer":
                return 0L;
            case "double":
                return 0.0;
            default:
                return null;
        }
    }
    
    /**
     * 获取变量值
     * @param name 变量名
     * @return 变量值
     */
    public Object getVariable(String name) {
        return variables.get(name);
    }
    
    /**
     * 设置变量值
     * @param name 变量名
     * @param value 变量值
     */
    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }
    
    /**
     * 更新变量值，根据类型进行转换
     * @param name 变量名
     * @param type 变量类型
     * @param value 变量值
     */
    public void updateVariable(String name, String type, Object value) {
        Object parsedValue = convertValue(type, value);
        variables.put(name, parsedValue);
    }
    
    /**
     * 转换值到指定类型
     * @param type 目标类型
     * @param value 原始值
     * @return 转换后的值
     */
    public Object convertValue(String type, Object value) {
        if (value == null) {
            return getDefaultValue(type);
        }
        
        // 如果类型已匹配，直接返回
        String valueType = getValueType(value);
        if (valueType.equalsIgnoreCase(type)) {
            return value;
        }
        
        // 类型转换
        switch (type.toLowerCase()) {
            case "string":
                return value.toString();
            case "integer":
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                } else {
                    return Long.parseLong(value.toString());
                }
            case "double":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else {
                    return Double.parseDouble(value.toString());
                }
            default:
                throw new IllegalArgumentException("Unsupported variable type: " + type);
        }
    }
    
    /**
     * 获取值的类型
     * @param value 值对象
     * @return 类型名称
     */
    private String getValueType(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer || value instanceof Long) {
            return "integer";
        } else if (value instanceof Double || value instanceof Float) {
            return "double";
        } else {
            return value.getClass().getSimpleName().toLowerCase();
        }
    }
    
    /**
     * 获取所有变量
     * @return 变量映射
     */
    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }
    
    /**
     * 合并变量
     * @param newVariables 要合并的变量
     */
    public void mergeVariables(Map<String, Object> newVariables) {
        if (newVariables != null) {
            variables.putAll(newVariables);
        }
    }
    
    /**
     * 清除所有变量
     */
    public void clear() {
        variables.clear();
    }
}
