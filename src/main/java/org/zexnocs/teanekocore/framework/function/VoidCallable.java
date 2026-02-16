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
     * Performs an operation, or throws an exception if unable to do so.
     *
     * @throws Exception if unable to perform the operation
     */
    void call() throws Exception;
}
