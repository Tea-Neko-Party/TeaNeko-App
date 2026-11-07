package org.zexnocs.teanekocore.event.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 事件服务接口。
 *
 * <br>2026/11/07：新增覆盖完整事件链的实际完成入口。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IEventService {
    /**
     * 推送事件
     * @param event 事件
     */
    default void pushEvent(@NonNull IEvent<?> event) {
        pushEventWithFuture(event).finish();
    }

    /**
     * 推送事件并获取一个 TaskFuture 对象，可以通过该对象获取事件异常或者等待事件处理完成。
     * @param event 事件
     * @return TaskFuture 对象，可以通过该对象获取事件异常或者等待事件处理完成。务必在事件处理完使用 .finish() 方法报告未处理的异常。
     */
    TaskFuture<ITaskResult<Void>> pushEventWithFuture(@NonNull IEvent<?> event);

    /**
     * @param event 原始事件
     * @return 2026/11/06：事件链与已登记异步业务均实际完成的阶段，用于入口容量回收
     */
    java.util.concurrent.CompletionStage<Void> pushEventWithCompletion(@NonNull IEvent<?> event);
}
