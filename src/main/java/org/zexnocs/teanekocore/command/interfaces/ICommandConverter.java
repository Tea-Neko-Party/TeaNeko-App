package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;

import java.util.concurrent.CompletableFuture;

/**
 * 指令转换器接口。将数据转换成指令数据对象。
 * 可能需要用到 ICommandParser 来解析数据。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public interface ICommandConverter<T> {
    /**
     * 将Object转化成泛型类型。
     * @param data 需要被转化的数据
     */
    @SuppressWarnings("unchecked")
    default T __convertToGeneric(Object data) throws ClassCastException {
        if(data == null) {
            return null;
        }
        return (T) data;
    }

    /**
     * 将数据解析成指令数据对象。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    default CompletableFuture<CommandData<?>> parse(Object data) throws ClassCastException {
        return __parse(__convertToGeneric(data));
    }

    /**
     * 将数据解析成指令数据对象。
     * 用于子类的实现。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    CompletableFuture<CommandData<?>> __parse(T data);
}
