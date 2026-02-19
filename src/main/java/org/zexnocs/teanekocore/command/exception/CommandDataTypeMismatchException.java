package org.zexnocs.teanekocore.command.exception;

/**
 * 如果 CommandData 不符合 Method 参数的要求，则直接抛出该异常，表示无法匹配。
 * 这属于经常发生的异常，不需要 report，但是需要处理。
 *
 * @author zExNocs
 * @date 2026/02/19
 */
public class CommandDataTypeMismatchException extends Exception {
    public CommandDataTypeMismatchException() {
        super();
    }
}
