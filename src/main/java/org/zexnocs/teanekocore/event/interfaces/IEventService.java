package org.zexnocs.teanekocore.event.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 事件服务接口。
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
}
