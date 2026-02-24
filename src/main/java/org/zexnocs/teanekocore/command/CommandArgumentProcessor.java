package org.zexnocs.teanekocore.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 支持 List<String>（或其他泛型 List<T>）参数，
 * 并修复：当 List 参数标注了 @DefaultValue 时，将默认值作为 List 的唯一元素。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Service("commandArgumentProcessor")
public class CommandArgumentProcessor implements ICommandArgumentProcessor {
    /// 日志
    private final ILogger logger;

    @Autowired
    public CommandArgumentProcessor(ILogger logger) {
        this.logger = logger;
    }

    /**
     * 处理指令参数。
     * @param method 指令方法
     * @param args 指令参数
     * @param commandData 指令数据。如果有参数是 CommandData 的话，可以直接使用
     * @return 处理后的参数
     */
    @Override
    public Object[] process(Method method, String[] args, CommandData<?> commandData) throws CommandDataTypeMismatchException {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            // 无参数，直接返回
            return new Object[0];
        }
        // 检查 method 是否符合 commandData 参数的要求，如果不符合，直接抛出异常
        for (Parameter parameter : parameters) {
            if (isCommandData(parameter.getType())) {
                validateCommandDataType(parameter, commandData);
            }
        }
        // 构建结果数组
        Object[] result = new Object[parameters.length];
        List<String> argList = args == null ? Collections.emptyList() : Arrays.asList(args);
        // 根据参数构建非 List 参数的剩余数量数组
        int[] nonListCount = buildNonListCount(parameters);
        boolean success = matchParameter(
                parameters,
                0,
                argList,
                0,
                result,
                commandData,
                method,
                nonListCount
        );
        return success ? result : null;
    }


    /**
     * 构建非 List 参数的剩余数量数组。
     *
     * @param parameters 方法参数数组
     * @return {@link int[] }
     */
    private int[] buildNonListCount(Parameter[] parameters) {
        int[] result = new int[parameters.length];
        for (int i = parameters.length - 2; i >= 0; i--) {
            Class<?> nextType = parameters[i + 1].getType();
            result[i] = result[i + 1] +
                    (isList(nextType) || isCommandData(nextType) ? 0 : 1);
        }
        return result;
    }


    /**
     * 匹配参数。
     *
     * @param parameters  方法参数数组
     * @param paramIndex  当前参数索引
     * @param args        指令参数列表
     * @param argIndex    当前指令参数索引
     * @param output      输出数组
     * @param commandData 指令数据
     * @param method      指令方法
     * @param nonListCount 非 List 参数在每个位置之后剩余数量的数组 非 List 参数的剩余数量数组
     * @return boolean
     */
    private boolean matchParameter(
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 全部参数匹配完成，后续参数可以丢弃
        if (paramIndex >= parameters.length) {
            return true;
        }

        // 处理当前参数
        Parameter param = parameters[paramIndex];
        Class<?> type = param.getType();
        if (isCommandData(type)) {
            // 如果是 CommandData 参数，直接使用传入的 commandData
            output[paramIndex] = commandData;
            return matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }
        if (isList(type)) {
            // 如果是 List 参数，尝试匹配多个参数
            return matchListParameter(
                    param,
                    parameters,
                    paramIndex,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }

        // 普通参数，尝试匹配一个参数
        return matchNormalParameter(
                param,
                parameters,
                paramIndex,
                args,
                argIndex,
                output,
                commandData,
                method,
                nonListCount
        );
    }


    /**
     * 匹配普通参数。
     *
     * @param param         当前参数
     * @param parameters    参数数组
     * @param paramIndex    当前参数索引
     * @param args          当前指令参数列表
     * @param argIndex      当前指令参数索引
     * @param output        输出数组
     * @param commandData   指令源数据
     * @param method        方法
     * @param nonListCount  非 List 参数在每个位置之后剩余数量的数组
     * @return boolean
     */
    private boolean matchNormalParameter(
            Parameter param,
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 尝试匹配一个参数，如果没有参数了，且有默认值，则使用默认值
        DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
        int remainingArgs = args.size() - argIndex;
        if (remainingArgs <= nonListCount[paramIndex] && defaultValue != null) {
            // 如果剩余参数不足以匹配后续非 List 参数，并且当前参数有默认值，则尝试使用默认值
            Object dv = resolveDefaultValue(param, defaultValue);
            if (dv != null) {
                output[paramIndex] = dv;
                if (matchParameter(
                        parameters,
                        paramIndex + 1,
                        args,
                        argIndex,
                        output,
                        commandData,
                        method,
                        nonListCount)) {
                    return true;
                }
            }
        }

        // 没有 DefaultValue，且没有参数来匹配，直接失败
        if (remainingArgs == 0) {
            return false;
        }
        // 尝试匹配一个参数
        Object converted = convertValue(args.get(argIndex), param.getType());
        if (converted == null) {
            // 如果转化失败，则尝试使用默认值（如果有）
            if (defaultValue == null) {
                return false;
            }
            Object dv = resolveDefaultValue(param, defaultValue);
            if (dv == null) {
                return false;
            }
            output[paramIndex] = dv;
            return matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex,
                    output,
                    commandData,
                    method,
                    nonListCount
            );
        }
        // 转化成功，继续匹配下一个参数
        output[paramIndex] = converted;
        return matchParameter(
                parameters,
                paramIndex + 1,
                args,
                argIndex + 1,
                output,
                commandData,
                method,
                nonListCount
        );
    }


    /**
     * 匹配 List 参数。
     *
     * @param param        当前参数
     * @param parameters   参数数组
     * @param paramIndex   当前参数索引
     * @param args         当前指令参数列表
     * @param argIndex     当前指令参数索引
     * @param output       输出数组
     * @param commandData  指令源数据
     * @param method       方法
     * @param nonListCount 非 List 参数在每个位置之后剩余数量的数组
     * @return boolean
     */
    private boolean matchListParameter(
            Parameter param,
            Parameter[] parameters,
            int paramIndex,
            List<String> args,
            int argIndex,
            Object[] output,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
        // 可以匹配的最大参数数量 = 总参数数量 - 当前参数索引 - 后续非 List 参数数量
        int max = args.size() - argIndex - nonListCount[paramIndex];
        if (max < 0) {
            // 如果 max < 0，说明剩余参数不足以匹配后续非 List 参数，直接失败
            return false;
        }
        // 获取 List 元素类型
        Class<?> elementType = getListElementType(param);
        List<Object> list = new ArrayList<>(max);
        // 尝试匹配多个参数，直到转化失败或达到 max
        for (int i = 0; i < max; i++) {
            // 转化参数，如果转化失败，则停止匹配 List 参数
            Object value = convertValue(args.get(argIndex + i), elementType);
            if (value == null) {
                break;
            }
            list.add(value);
        }
        // 尝试匹配剩余参数，如果 List 参数匹配了 size 个参数，则继续匹配下一个参数
        for (int size = list.size(); size > 0; size--) {
            output[paramIndex] = new ArrayList<>(list.subList(0, size));
            // 如果匹配成功，直接返回 true
            if (matchParameter(
                    parameters,
                    paramIndex + 1,
                    args,
                    argIndex + size,
                    output,
                    commandData,
                    method,
                    nonListCount))
                return true;
        }
        // 如果没有参数匹配成功，但 List 参数有默认值，则使用默认值（作为 List 的唯一元素）
        if (defaultValue != null) {
            Object dv = convertValue(defaultValue.value(), elementType);
            if (dv == null) {
                logger.errorWithReport(this.getClass().getSimpleName(), """
                                列表参数的默认值转化失败，无法使用默认值
                                 - 参数: %s
                                 - 默认值: %s
                                 - 元素类型: %s
                                """.formatted(param.getName(), defaultValue.value(), elementType.getName()));
                return false;
            }
            output[paramIndex] = Collections.singletonList(dv);
        } else {
            output[paramIndex] = Collections.emptyList();
        }
        // 继续匹配下一个参数
        return matchParameter(
                parameters,
                paramIndex + 1,
                args,
                argIndex,
                output,
                commandData,
                method,
                nonListCount
        );
    }

    /**
     * 解析默认值。
     *
     * @param param        当前参数
     * @param defaultValue 默认值注解
     * @return {@link Object }
     */
    private Object resolveDefaultValue(
            Parameter param,
            DefaultValue defaultValue
    ) {
        // 转化默认值，如果转化失败，记录错误日志并返回 null
        Object value = convertValue(defaultValue.value(), param.getType());
        if (value == null) {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            转化默认值失败，无法使用默认值
                             - 参数: %s
                             - 默认值: %s
                             - 目标类型: %s
                            """.formatted(param.getName(), defaultValue.value(), param.getType().getName()));
        }
        return value;
    }

    /**
     * 获取 List 元素类型。
     * @param param List 参数
     * @return      List 元素类型，如果无法获取则默认为 String.class
     */
    private static Class<?> getListElementType(Parameter param) {
        Type type = param.getParameterizedType();
        if (type instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return String.class;
    }


    /**
     * 转化参数值。
     *
     * @param input 输入字符串
     * @param type  类型
     * @return {@link Object } 转化后的值，如果转化失败则返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(
            String input,
            Class<?> type
    ) {
        try {
            if (type == String.class)
                return input;
            if (type == int.class || type == Integer.class)
                return Integer.parseInt(input);
            if (type == long.class || type == Long.class)
                return Long.parseLong(input);
            if (type == float.class || type == Float.class)
                return Float.parseFloat(input);
            if (type == double.class || type == Double.class)
                return Double.parseDouble(input);
            if (type == boolean.class || type == Boolean.class)
                return Boolean.parseBoolean(input);
            if (type.isEnum())
                return Enum.valueOf((Class) type, input);
        }
        catch (Exception ignore) {}
        return null;
    }


    /**
     * 判断是否为 List 类型。
     *
     * @param type 类型
     * @return boolean
     */
    private static boolean isList(Class<?> type) {
        return List.class.isAssignableFrom(type);
    }


    /**
     * 判断是否为 CommandData 类型。
     *
     * @param type 类型
     * @return boolean
     */
    private static boolean isCommandData(Class<?> type) {
        return CommandData.class.isAssignableFrom(type);
    }


    /**
     * 验证 CommandData 类型是否匹配 Method 参数的要求。
     *
     * @param parameter   Method 参数
     * @param commandData 指令数据
     * @throws CommandDataTypeMismatchException 如果CommandData不符合Method参数的要求，则直接抛出该异常，表示无法匹配。
     */
    public static void validateCommandDataType(
            Parameter parameter,
            CommandData<?> commandData
    ) throws CommandDataTypeMismatchException {
        // 获取参数的泛型类型，如果不是 ParameterizedType，则抛出异常
        Type methodType = parameter.getParameterizedType();
        if (!(methodType instanceof ParameterizedType pt)) {
            throw new CommandDataTypeMismatchException();
        }
        Class<?> expected = (Class<?>) pt.getActualTypeArguments()[0];
        // 获取 CommandData 的泛型类型参数，如果无法获取或不合法，则抛出异常
        Class<?> actualClass = commandData.getRawDataType();
        // 判断实际类型是否与预期类型兼容，如果不兼容，则抛出异常
        if (!expected.isAssignableFrom(actualClass)) throw new CommandDataTypeMismatchException();
    }
}
