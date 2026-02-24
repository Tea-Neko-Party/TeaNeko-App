package org.zexnocs.teanekocore.command.exception;

/**
 * 如果 {@link org.zexnocs.teanekocore.command.CommandData} 不符合 {@code Method} 参数的要求，则直接抛出该异常，表示无法匹配。
 * <p>这属于经常发生的异常，不需要 report，但是需要处理。
 *
 * @see org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher
 * @see org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor
 * @author zExNocs
 * @date 2026/02/19
 * @since 4.0.0
 */
public class CommandDataTypeMismatchException extends Exception {
    public CommandDataTypeMismatchException() {
        super();
    }
}
