package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.Nullable;

/**
 * 分发终态。COMPLETED 只表示处理链结束，value 才可能包含具体业务结果。
 *
 * @param status 本次处理的终止状态
 * @param value 待处理的值
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record CommandResult(Status status, @Nullable Object value) {
    /**
     * 命令分发与执行的终止状态，不把正常 Void 完成解释为具体业务成功。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status {
        NOT_MATCHED, DISABLED, CANCELLED, METHOD_NOT_FOUND, INVALID_ARGUMENTS,
        DATA_TYPE_MISMATCH, OUT_OF_SCOPE, PERMISSION_DENIED, HELP, COMPLETED
    }

    /**
     * 为没有业务返回值的终止分支创建结构化回执，状态与业务成功含义保持分离。
     *
     * @param status 本次处理的终止状态
     * @return 区分分发终止分支与业务值的结构化回执
     */
    public static CommandResult of(Status status) {
        return new CommandResult(status, null);
    }
}
