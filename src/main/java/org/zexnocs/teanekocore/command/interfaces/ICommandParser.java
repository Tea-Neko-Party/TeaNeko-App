package org.zexnocs.teanekocore.command.interfaces;

import java.util.Collection;

/**
 * 指令解析器接口。将数据解析成 String 用于指令处理器。
 * 如果要注册解析器到 scanner，可以加上 @CommandParser 注解。
 *
 * @param <T> 需要被转化成指令数据对象的类型
 * @author zExNocs
 * @date 2026/02/18
 */
public interface ICommandParser<T> {
    /**
     * 将Object转化成泛型类型。
     * @param data 需要被转化的数据
     */
    @SuppressWarnings("unchecked")
    default T __convertToGeneric(Object data) throws ClassCastException {
        return (T) data;
    }

    /**
     * 将数据解析成指令数据对象。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    default Collection<String> parse(Object data) throws ClassCastException {
        return __parse(__convertToGeneric(data));
    }

    /**
     * 将数据解析成指令数据对象。
     * 用于子类的实现。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    Collection<String> __parse(T data);
}
