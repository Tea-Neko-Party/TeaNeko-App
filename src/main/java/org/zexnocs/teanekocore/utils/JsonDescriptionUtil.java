package org.zexnocs.teanekocore.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Ignore;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;

/**
 * 将数据类转化成 JSON 的描述字符串
 * 格式：
 * 1. 每个域都要有 @Description 注解来描述该域的含义
 * 2. 如果没有 @Description 注解，则不会描述该域，但是会保留该域
 * 3. 如果不需要该域，则需要在类定义时使用 @Ignore 注解来忽略该域
 *
 * @author zExNocs
 * @date 2026/02/12
 */
@Service
public class JsonDescriptionUtil {
    private final ConcurrentMapCacheContainer<Class<?>, String> cache;

    @Autowired
    public JsonDescriptionUtil(ICacheService iCacheService) {
        this.cache = ConcurrentMapCacheContainer.of(iCacheService);
    }


    /**
     * 将数据类转化成 JSON 的描述字符串
     * 格式：
     * 1. 每个域都要有 @Description 注解来描述该域的含义
     * 2. 如果没有 @Description 注解，则不会描述该域，但是会保留该域
     * 3. 如果不需要该域，则需要在类定义时使用 @Ignore 注解来忽略该域
     * @param clazz 数据类的 Class 对象
     * @return JSON 字符串描述
     */
    public String toJsonDescription(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, key -> {
            var sb = new StringBuilder();
            var closeSet = new HashSet<Class<?>>();
            _processObject(clazz, sb, closeSet);
            return sb.toString();
        });
    }

    /**
     * 处理类的字段，递归生成 JSON 描述
     * @param clazz 类的 Class 对象
     * @param sb 字符串构建器
     */
    private static void _processObject(Class<?> clazz, StringBuilder sb, HashSet<Class<?>> closeSet) {
        // 添加大括号
        sb.append("{");
        // 放入到闭环集合中
        closeSet.add(clazz);
        // 遍历字段
        boolean flag = false;
        var fields = clazz.getDeclaredFields();
        for(var field : fields) {
            // 跳过被 @Ignore 注解的字段
            if (field.isAnnotationPresent(Ignore.class)) {
                continue;
            }
            flag = true;
            // 添加字段名称
            sb.append("\"").append(field.getName()).append("\": ");

            // 解析字段类型和描述
            Class<?> type = field.getType();

            if (List.class.isAssignableFrom(type)) {
                // List
                _handleList(field, sb, closeSet);
            } else if (_isSimpleType(type)) {
                // 简单类型
                sb.append(_simpleTypeName(type));
            } else if (type.isEnum()) {
                // Enum
                _handleEnum(field, sb);
            } else {
                // Object
                sb.append("object<").append(type.getSimpleName()).append(">");
                if (closeSet.contains(type)) {
                    sb.append("{ /* ...circular reference... */ }");
                } else {
                    _processObject(type, sb, closeSet);
                }
            }

            // 添加描述和分号
            _appendDescription(sb, _getDescription(field));
            sb.append(", ");
        }
        // 删除最后的逗号和空格
        if (flag) {
            sb.setLength(sb.length() - 2);
        }
        // 添加闭括号
        sb.append("}");
    }

    /**
     * 处理 List 类型字段
     * @param field 字段
     * @param sb 字符串构建器
     */
    private static void _handleList(Field field, StringBuilder sb, HashSet<Class<?>> closeSet) {
        sb.append("array[");

        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                if (_isSimpleType(clazz)) {
                    sb.append(_simpleTypeName(clazz));
                } else if (clazz.isEnum()) {
                    // Enum
                    _handleEnum(field, sb);
                } else {
                    _processObject(clazz, sb, closeSet);
                }
            }
        } else {
            sb.append("object");
        }

        sb.append("]");
    }

    /**
     * 处理 enum 类型字段
     * @param field 字段
     * @param sb 字符串构建器
     */
    private static void _handleEnum(Field field, StringBuilder sb) {
        Class<?> type = field.getType();
        sb.append("enum[");
        Object[] constants = type.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            sb.append(constants[i].toString());
            if (i < constants.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
    }

    // ===== 工具方法 =====

    /**
     * 是否是简单类型
     * @param type 字段类型
     * @return 是否是简单类型
     */
    private static boolean _isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class;
    }

    /**
     * 获取简单类型名称
     * @param type 字段类型
     * @return 简单类型名称
     */
    private static String _simpleTypeName(Class<?> type) {
        if (type == Integer.class || type == Long.class || type == Short.class || type == Byte.class || type == Character.class
                || type == int.class || type == long.class || type == short.class || type == byte.class || type == char.class
        ) return "integer";

        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == Double.class || type == Float.class
                || type == double.class || type == float.class
        ) return "number";
        if (type == String.class) return "string";
        return type.getSimpleName();
    }

    /**
     * 获取字段的描述
     * @param field 字段
     * @return 描述字符串
     */
    private static String _getDescription(Field field) {
        var d = field.getAnnotation(Description.class);
        return d != null ? d.value() : null;
    }

    /**
     * 在字符串构建器中添加描述注释
     * @param sb 字符串构建器
     * @param desc 描述字符串
     */
    private static void _appendDescription(StringBuilder sb, String desc) {
        if (desc != null && !desc.isBlank()) {
            sb.append(" /* ").append(desc).append(" */");
        }
    }
}
