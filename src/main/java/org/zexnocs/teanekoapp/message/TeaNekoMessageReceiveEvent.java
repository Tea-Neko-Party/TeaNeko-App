package org.zexnocs.teanekoapp.message;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.event.AbstractEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 从客户端接收到 message 时触发的事件。
 * <p>客户端方面一般实现一个新的事件来继承该事件，并提供详细的泛型类型。
 * <p>监听器方面如果：
 * <p>- 处理所有 message，可以直接监听 {@code TeaNekoMessageReceiveEvent<?>};
 * 注意不要在 {@code ?} 处使用具体类型，因为依然会导致非该类型的事件被监听到。
 * <p>- 如果要处理特定类型的 message，请直接监听客户端中继承的事件。
 * <p>例如，客户端实现了
 * {@code AEvent extends TeaNekoMessageReceiveEvent<ASpecificMessage>}
 * 则监听器可以监听
 * {@code AEvent}
 * 来处理
 * {@code ASpecificMessage}
 * 而不能监听
 * {@code TeaNekoMessageReceiveEvent<ASpecificMessage>}
 * 来处理，因为会导致非
 * {@code ASpecificMessage}
 * 的事件也被监听到。这是因为事件的分类不是根据泛型类型的，而是根据事件类的。
 *
 * 修改记录：<br>
 * 2026/11/04：监听器可登记异步业务完成阶段，网页入站不再将事件通知完成当成业务完成。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 * @version 4.7.5-l
 */
public class TeaNekoMessageReceiveEvent<T extends ITeaNekoMessageData> extends AbstractEvent<T> {
    private final List<CompletableFuture<?>> processing = new ArrayList<>();
    private boolean processingSealed;

    /**
     * 2026/11/04：只在同步通知监听器期间登记，不阻塞事件执行线程。
     *
     * @param stage 已启动的真实异步业务
     * @throws IllegalStateException 当事件通知已结束后仍试图登记阶段时
     */
    public synchronized void trackProcessing(CompletionStage<?> stage) {
        if (processingSealed) {
            throw new IllegalStateException("消息通知已结束，不能追加异步处理阶段。");
        }
        processing.add(stage.toCompletableFuture());
    }

    /**
     * 2026/11/04：通知完成后封闭本次业务集合，失败继续传播，不声称撤销已发生的副作用。
     *
     * @return 所有已登记业务实际结束后的完成阶段
     */
    public synchronized CompletionStage<Void> processingCompletion() {
        processingSealed = true;
        return CompletableFuture.allOf(processing.toArray(CompletableFuture<?>[]::new)).minimalCompletionStage();
    }
    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     */
    public TeaNekoMessageReceiveEvent(@Nullable T data) {
        super(data);
    }
}
