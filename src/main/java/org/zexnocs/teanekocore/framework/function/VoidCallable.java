package org.zexnocs.teanekocore.framework.function;

/**
 * 一个不接受任何参数且没有返回值的函数接口。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@FunctionalInterface
public interface VoidCallable {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @throws Exception if unable to compute a result
     */
    void call() throws Exception;
}
