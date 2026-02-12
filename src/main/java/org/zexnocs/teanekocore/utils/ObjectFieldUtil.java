package org.zexnocs.teanekocore.utils;

import org.jspecify.annotations.NonNull;
import org.springframework.data.util.Pair;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 设置一个类的字段值的工具类。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public enum ObjectFieldUtil {
    Instance;

    /**
     * 设置指定对象的指定字段的值。
     * @param objectMapper 用于解析字符串的 ObjectMapper 实例
     * @param obj   目标对象
     * @param field 字段名
     * @param value 要设置的值。由字符串自动解析成相应类型。
     * @return 修改字段值后的对象
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalAccessException 如果无法访问字段
     * @throws IllegalArgumentException 如果字段值不合法
     */
    public Object setFieldValue(ObjectMapper objectMapper, Object obj, String field, String value)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        if (obj == null || field == null || field.isBlank()) {
            throw new IllegalArgumentException("对象或字段名不能为空");
        }

        Class<?> clazz = obj.getClass();
        // 尝试获取字段，如果不存在抛出 NoSuchFieldException
        var f = getFieldFromClassHierarchy(clazz, field);
        f.setAccessible(true);
        // 尝试读取字段值，如果无法访问则抛出 IllegalAccessException
        var fieldType = f.getType();
        if(fieldType == String.class) {
            // 如果字段类型是 String，直接设置值
            f.set(obj, value);
        } else if (List.class.isAssignableFrom(fieldType)) {
            // 如果是 List 类型，尝试解析为 List
            var elementType = getListElementType(f);
            f.set(obj, parseValue(objectMapper, value, fieldType, elementType));
        } else {
            // 否则直接解析为目标类型
            f.set(obj, parseValue(objectMapper, value, fieldType));
        }
        return obj; // 返回修改后的对象
    }

    /**
     * 将一个字符串值添加到指定对象的 List 字段中。
     * @param objectMapper 用于解析字符串的 ObjectMapper 实例
     * @param obj 目标对象
     * @param fieldName 字段名，必须是 List 类型的字段
     * @param value 要添加的值，通常是一个 JSON 字符串表示的对象
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalArgumentException 如果无法解析值或添加到 List 中
     * @throws IllegalAccessException 如果无法访问字段
     * @throws FieldNotListException 如果字段不是 List 类型
     */
    public Object addToListField(ObjectMapper objectMapper,
                               Object obj,
                               String fieldName,
                               String value)
            throws NoSuchFieldException, FieldNotListException, IllegalArgumentException, IllegalAccessException {
        var pair = getListFieldPair(obj, fieldName);
        var list = pair.getSecond();
        Class<?> elementType = getListElementType(pair.getFirst());
        list.add(parseValue(objectMapper, value, elementType));
        return obj;
    }

    /**
     * 根据 index 删除指定对象的 List 字段中的元素。
     * @param obj 目标对象
     * @param fieldName 字段名，必须是 List 类型的字段
     * @index 索引位置
     * @return 修改后的对象
     * @throws IllegalAccessException 如果无法访问字段
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws FieldNotListException 如果字段不是 List 类型
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public Object removeFromListField(Object obj, String fieldName, int index)
            throws IllegalAccessException, NoSuchFieldException, FieldNotListException, IndexOutOfBoundsException {
        var list = getListFieldPair(obj, fieldName).getSecond();
        list.remove(index);
        return obj;
    }

    /**
     * 清理指定对象的 List 字段中的所有元素。
     * @param obj 目标对象
     * @param fieldName 字段名，必须是 List 类型的字段
     * @return 修改后的对象
     * @throws IllegalAccessException 如果无法访问字段
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws FieldNotListException 如果字段不是 List 类型
     */
    public Object clearListField(Object obj, String fieldName)
            throws IllegalAccessException, NoSuchFieldException, FieldNotListException {
        var list = getListFieldPair(obj, fieldName).getSecond();
        list.clear();
        return obj;
    }

    /**
     * 根据 fieldName 获取对象的 List 字段值。
     * @param obj 目标对象
     * @param fieldName 字段名
     * @return 字段值
     * @throws IllegalAccessException 如果无法访问字段
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws FieldNotListException 如果字段不是 List 类型
     */
    @SuppressWarnings("unchecked")
    public Pair<Field, List<Object>> getListFieldPair(Object obj, String fieldName)
            throws IllegalAccessException, NoSuchFieldException, FieldNotListException {
        // 获取字段，如果不存在抛出 NoSuchFieldException
        var field = getFieldFromClassHierarchy(obj.getClass(), fieldName);
        // 尝试读取字段值，如果无法访问则抛出 IllegalAccessException
        field.setAccessible(true);
        Object value = field.get(obj);
        if(value == null) {
            // 如果字段值为 null，则初始化一个空的 List
            value = new CopyOnWriteArrayList<>();
            field.set(obj, value);
        }
        // 判断字段值是否是 List 类型，如果不是则抛出 FieldNotListException
        if (!List.class.isAssignableFrom(field.getType())) {
            throw new FieldNotListException(fieldName, field.getType());
        }
        return Pair.of(field, (List<Object>) value);
    }

    /**
     * 简单返回 List 元素类型。
     * @param listField List 字段
     * @return List 元素类型，如果无法确定则返回 Object.class
     */
    public Class<?> getListElementType(Field listField) {
        assert List.class.isAssignableFrom(listField.getType())
                : "字段 " + listField.getName() + " 不是 List 类型";
        // 获取 List 的泛型类型
        var genericType = listField.getGenericType();
        if (genericType instanceof java.lang.reflect.ParameterizedType) {
            var typeArguments = ((java.lang.reflect.ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> clazz) {
                // 如果有泛型参数且是 Class 类型，返回该类型
                return clazz;
            }
        }
        // 如果无法确定类型，返回 Object.class
        return Object.class;
    }

    /**
     * 根据字段名获取该类及其父类中的字段。
     * @param clazz 类
     * @param fieldName 字段名
     * @return 类中的字段
     * @throws NoSuchFieldException 如果未找到指定字段
     */
    public Field getFieldFromClassHierarchy(@NonNull Class<?> clazz, String fieldName)
            throws NoSuchFieldException {
        var clazzForLoop = clazz;
        while (clazzForLoop != null) {
            try {
                return clazzForLoop.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazzForLoop = clazzForLoop.getSuperclass();
            }
        }
        throw new NoSuchFieldException("未找到字段 '" + fieldName + "' 在类 " + clazz.getName() + " 中。");
    }

    /**
     * 解析一个字符串值为指定类型的对象。(不带元素类型)
     * @param objectMapper 用于解析字符串的 ObjectMapper 实例
     * @param value 要解析的字符串值
     * @param targetType 目标类型
     * @return 解析后的对象
     * @throws IllegalArgumentException 如果无法解析值为目标类型
     */
    public Object parseValue(ObjectMapper objectMapper, String value, Class<?> targetType)
            throws IllegalArgumentException {
        return parseValue(objectMapper, value, targetType, null);
    }

    /**
     * 解析一个字符串值为指定类型的对象。
     * @param objectMapper 用于解析字符串的 ObjectMapper 实例
     * @param value 要解析的字符串值
     * @param targetType 目标类型
     * @param elementType 如果目标类型是 List，则为 List 的元素类型
     * @return 解析后的对象
     * @throws IllegalArgumentException 如果无法解析值为目标类型
     */
    public Object parseValue(ObjectMapper objectMapper, String value, Class<?> targetType, Class<?> elementType)
            throws IllegalArgumentException {
        try {
            // 如果是 List，则不能直接用 Json 解析
            if (List.class.isAssignableFrom(targetType)) {
                // 获取 List 的元素类型
                var javaType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, elementType != null ? elementType : Object.class);
                List<?> tempList = objectMapper.readValue(value, javaType);
                return new CopyOnWriteArrayList<>(tempList);
            } else {
                // 否则直接解析为目标类型
                return objectMapper.readValue(value, targetType);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析值 '" + value + "' 为类型 " + targetType.getName(), e);
        }
    }

    /**
     * 表示字段不是 List 类型的异常。
     */
    public static class FieldNotListException extends RuntimeException {
        public FieldNotListException(String fieldName, Class<?> actualType) {
            super("字段 '" + fieldName + "' 不是 List 类型，而是 " + actualType.getName());
        }
    }
}
