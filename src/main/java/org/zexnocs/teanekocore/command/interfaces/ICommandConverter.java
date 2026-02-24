package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.framework.pair.Pair;

/**
 * 指令转换器接口。将数据转换成指令数据对象。
 *
 * @param <T> 需要被转化的数据的类型
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandConverter<T> {
    /**
     * 将数据解析成指令数据对象。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    default CommandData<?> parse(Object data) throws ClassCastException {
        return __parse(__convertToGeneric(data));
    }

    /**
     * 根据
     * {@code T}
     * 获取到
     * {@code Pair.of(scope, scopeId)}
     *
     * @param data 需要被转化的数据
     * @return {@link Pair }<{@link CommandScope }, {@link String }>
     */
    Pair<CommandScope, String> getScopeAndScopeId(T data);

    /**
     * 将Object转化成泛型类型。
     * @param data 需要被转化的数据
     */
    @SuppressWarnings("unchecked")
    private T __convertToGeneric(Object data) throws ClassCastException {
        if(data == null) {
            return null;
        }
        return (T) data;
    }

    /**
     * 将数据解析成指令数据对象。
     * 用于子类的实现。
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    CommandData<T> __parse(T data);
}
