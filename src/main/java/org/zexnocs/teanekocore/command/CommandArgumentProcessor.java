package org.zexnocs.teanekocore.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.DefaultValue;
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
 */
@Service("commandArgumentProcessor")
public class CommandArgumentProcessor implements ICommandArgumentProcessor {

    /// 日志记录器
    private final ILogger logger;

    @Autowired
    public CommandArgumentProcessor(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public Object[] process(Method method, String[] args, CommandData<?> commandData) {
        Parameter[] parameters = method.getParameters();
        if(parameters.length == 0) {
            // 无参数，直接返回空数组
            return new Object[0];
        }
        Object[] convertedArgs = new Object[parameters.length];
        List<String> argList = args == null ? Collections.emptyList() : Arrays.asList(args);
        // 倒序预处理参数，计算每个 arg 后续非 List/CommandData 参数的数量
        int[] nonListCount = new int[parameters.length];
        nonListCount[parameters.length - 1] = 0; // 最后一个参数后没有非 List/CommandData 参数
        for (int i = parameters.length - 2; i >= 0; i--) {
            // 获取其后一个参数的类型
            Class<?> type = parameters[i + 1].getType();
            if (CommandData.class.isAssignableFrom(type) || List.class.isAssignableFrom(type)) {
                // 如果是 CommandData 或 List，则后续非 List/CommandData 参数数量不变
                nonListCount[i] = nonListCount[i + 1];
            } else {
                // 普通参数计入非 List 参数
                nonListCount[i] = nonListCount[i + 1] + 1;
            }
        }

        // 从参数 index=0、args 索引 argIdx=0 开始递归匹配
        boolean matched = __matchParameters(parameters, 0,
                argList, 0,
                convertedArgs,
                commandData, method, nonListCount);
        if (!matched) {
            return null;
        }
        return convertedArgs;
    }

    /**
     * 递归匹配 parameters[paramIdx...] 使用 argList[argIdx...]，并将转换结果填入 convertedArgs。
     *
     * @param parameters    方法的所有参数
     * @param paramIdx      当前匹配到的参数索引
     * @param argList       输入的参数列表（String）
     * @param argIdx        在 argList 中下一次可用的位置
     * @param convertedArgs 最终返回的 Object[]，存放各个参数的值
     * @param commandData   如果参数类型是 CommandData，直接赋值
     * @param method        当前 Method 对象，仅用于日志
     * @param nonListCount  预处理的参数列表，记录每个参数后续非 List/CommandData 参数的数量
     * @return 如果能完全匹配，则返回 true；匹配失败则返回 false
     */
    private boolean __matchParameters(
            Parameter[] parameters,
            int paramIdx,
            List<String> argList,
            int argIdx,
            Object[] convertedArgs,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 全部参数匹配完毕：后续 argList 可以丢弃
        if (paramIdx >= parameters.length) {
            return true;
        }
        // 如果 argIdx 超出范围，还没来得及匹配当前参数
        // 如果为 ==，则会尝试使用默认值
        if (argIdx > argList.size()) {
            return false;
        }

        Parameter currentParam = parameters[paramIdx];
        Class<?> paramType = currentParam.getType();
        DefaultValue defaultAnno = currentParam.getAnnotation(DefaultValue.class);

        // 1. 如果参数是 CommandData 类型，不消耗输入参数直接塞进去
        if (CommandData.class.isAssignableFrom(paramType)) {
            convertedArgs[paramIdx] = commandData;
            return __matchParameters(parameters, paramIdx + 1,
                    argList, argIdx,
                    convertedArgs,
                    commandData, method, nonListCount);
        }

        // 2. 如果参数是 List 类型，启动枚举 listSize 分配逻辑
        if (List.class.isAssignableFrom(paramType)) {
            return __matchListParameter(
                    currentParam,
                    defaultAnno,
                    parameters,
                    paramIdx,
                    argList,
                    argIdx,
                    convertedArgs,
                    commandData,
                    method,
                    nonListCount
            );
        }

        // 3. 如果是其他普通参数类型，尝试转换 argList[argIdx] 到目标类型
        // 判断是否需要使用默认值，因为优先对前面的参数使用默认值，判断剩下的 arg 是否足够
        boolean hasTriedDefaultValue = false;
        int remainingArgs = argList.size() - argIdx;
        if (remainingArgs <= nonListCount[paramIdx] && defaultAnno != null) {
            Object dv = _useDefaultValue(currentParam, defaultAnno, method);
            if (dv == null) {
                return false;
            }
            convertedArgs[paramIdx] = dv;
            // 避免重复尝试默认值（剪枝）
            hasTriedDefaultValue = true;
            // 不消耗当前输入参数尝试匹配下一个参数
            if(__matchParameters(parameters, paramIdx + 1,
                    argList, argIdx,
                    convertedArgs,
                    commandData, method, nonListCount)) {
                return true;
            }
        }

        // 先判断是否有足够的输入参数
        if(remainingArgs == 0) {
            // 没有输入参数了，无法匹配当前参数
            return false;
        }

        // 尝试把 argList[argIdx] 转成目标类型
        String rawValue = argList.get(argIdx);
        Object converted = _convertToType(rawValue, paramType);
        if (converted == null) {
            // 转换失败，尝试使用默认值。
            if(defaultAnno == null || hasTriedDefaultValue) {
                // 没有默认值，或者已经尝试过默认值，则直接返回 false
                return false;
            }
            // 如果有默认值，尝试使用默认值
            Object dv = _useDefaultValue(currentParam, defaultAnno, method);
            if (dv == null) {
                return false;
            }
            convertedArgs[paramIdx] = dv;
            return __matchParameters(parameters, paramIdx + 1,
                    argList, argIdx,
                    convertedArgs,
                    commandData, method, nonListCount);
        }

        // 转换成功，消费一个 arg，继续
        convertedArgs[paramIdx] = converted;
        return __matchParameters(parameters, paramIdx + 1,
                argList, argIdx + 1,
                convertedArgs,
                commandData, method, nonListCount);
    }

    /**
     * 获取 List 参数的泛型类型。
     * @param currentParam 当前参数
     * @return 返回 List 的泛型类型，如果无法解析则默认返回 String.class。
     */
    private static Class<?> getListGenericType(Parameter currentParam) {
        Type genericType = currentParam.getParameterizedType();
        Class<?> listElementClass = String.class; // 默认当作 String
        if (genericType instanceof ParameterizedType) {
            Type[] actualTypes = ((ParameterizedType) genericType).getActualTypeArguments();
            if (actualTypes.length == 1 && actualTypes[0] instanceof Class) {
                listElementClass = (Class<?>) actualTypes[0];
            }
        }
        return listElementClass;
    }

    /**
     * 处理 List 参数的匹配逻辑。
     * 如果参数是 List，则需要枚举所有可能的 listSize 分配方式。
     */
    private boolean __matchListParameter(
            Parameter currentParam,
            DefaultValue defaultAnno,
            Parameter[] parameters,
            int paramIdx,
            List<String> argList,
            int argIdx,
            Object[] convertedArgs,
            CommandData<?> commandData,
            Method method,
            int[] nonListCount
    ) {
        // 最大能给这个 List 分配的元素数 = 剩余 args 数 - 后续非 List 参数数
        int maxPossible = argList.size() - argIdx - nonListCount[paramIdx];
        if (maxPossible < 0) {
            // 即便给 List 0 个，后续非 List 参数也不够输入
            return false;
        }

        // 填充当前 list
        var convertedList = new ArrayList<>();
        convertedArgs[paramIdx] = convertedList;
        Class<?> listElementClass = getListGenericType(currentParam);
        for(int i = 0; i < maxPossible; i++) {
            String raw = argList.get(argIdx + i);
            if (listElementClass == String.class) {
                convertedList.add(raw);
            } else {
                var cv = _convertToType(raw, listElementClass);
                // 转化失败，则直接跳出循环
                if (cv == null) {
                    break;
                }
                convertedList.add(cv);
            }
        }
        // 逐步尝试从最大的 listSize 开始，不断删除尾部元素
        // 确保“尽可能多”地给当前 List 元素
        while(!convertedList.isEmpty()) {
            // 消耗了 size 个 args，递归匹配下一个参数
            if (__matchParameters(parameters, paramIdx + 1,
                    argList, argIdx + convertedList.size(),
                    convertedArgs,
                    commandData, method, nonListCount)) {
                return true;
            }
            // 否则删除尾部元素，尝试更小的 listSize
            convertedList.removeLast();
        }
        // list 为空，要么作为空列表返回，要么使用默认值作为唯一元素
        if (defaultAnno != null) {
            // 将默认字符串作为单元素列表
            String rawDefault = defaultAnno.value();
            Object convertedElement = _convertToType(rawDefault, listElementClass);
            if (convertedElement == null) {
                // 默认值转换失败，说明代码有问题。
                logger.errorWithReport("CommandArgumentProcessor",
                        "默认值转化失败。方法：" + method.getName() +
                                "，参数: " + currentParam.getName() +
                                "，默认值: " + defaultAnno.value() +
                                "，要求类型: " + listElementClass.getName());
                return false;
            }
            convertedList.add(convertedElement);
        }
        // 未消耗任何输入，进行下一个参数的填充。
        return __matchParameters(parameters, paramIdx + 1,
                argList, argIdx,
                convertedArgs,
                commandData, method, nonListCount);
    }

    /**
     * 将 @DefaultValue 中的字符串转换为参数类型，并返回该对象。
     * 如果转换失败，则返回 null 并记录错误日志。
     */
    private Object _useDefaultValue(Parameter parameter, DefaultValue defaultValue, Method method) {
        String strVal = defaultValue.value();
        Class<?> targetType = parameter.getType();
        Object convertValue = _convertToType(strVal, targetType);
        if (convertValue == null) {
            logger.errorWithReport("CommandArgumentProcessor",
                    "默认值转化失败。方法：" + method.getName() +
                            "，参数: " + parameter.getName() +
                            "，默认值: " + defaultValue.value() +
                            "，要求类型: " + targetType.getName());
            return null;
        }
        return convertValue;
    }

    /**
     * 按照原有逻辑，把字符串转换成基本类型或其包装类型。
     * 失败返回 null。
     */
    private Object _convertToType(String input, Class<?> type) {
        if (input == null) {
            return null;
        }
        try {
            if (type == String.class) {
                return input;
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(input);
            } else if (type == Long.class || type == long.class) {
                return Long.parseLong(input);
            } else if (type == Float.class || type == float.class) {
                return Float.parseFloat(input);
            } else if (type == Double.class || type == double.class) {
                return Double.parseDouble(input);
            } else if (type == Boolean.class || type == boolean.class) {
                return Boolean.parseBoolean(input);
            }
            // 这里如果要支持更多类型（例如枚举、Date 等），可在此处扩展
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
