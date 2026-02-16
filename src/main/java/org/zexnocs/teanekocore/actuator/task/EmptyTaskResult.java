package org.zexnocs.teanekocore.actuator.task;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 简单的空结果类，用于表示没有结果的任务。
 * 适用于不需要返回结果的任务。
 * @author zExNocs
 * @date 2026/02/10
 */
public enum EmptyTaskResult implements ITaskResult<Void> {
    INSTANCE;

    /**
     * @return 始终返回 true，表示任务成功完成。
     */
    @Override
    public boolean isSuccess() {
        return true;
    }

    /**
     * 始终返回 null，因为没有结果。
     * @return null
     */
    @Override
    public @Nullable Void getResult() {
        return null;
    }

    /**
     * 获取结果类型，始终返回 Void.class。
     *
     * @return Void.class
     */
    public static Class<Void> getResultType() {
        return Void.class;
    }
}
