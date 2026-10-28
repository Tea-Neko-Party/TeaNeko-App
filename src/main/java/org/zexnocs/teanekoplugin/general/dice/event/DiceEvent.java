package org.zexnocs.teanekoplugin.general.dice.event;

import lombok.Getter;
import org.zexnocs.teanekocore.event.AbstractEvent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 同步事件监听器可登记业务完成链；EventService 仍只负责原有事件通知。
 *
 * @param <T> 组件值类型
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
public abstract class DiceEvent<T> extends AbstractEvent<T> {
    /**
     * -- GETTER --
     *  提供事件处理中登记的业务与反馈链，供命令统一等待。
     */
    private CompletionStage<Void> completion = CompletableFuture.completedStage(null);

    /**
     * 以业务数据初始化骰子事件，异步业务或替代反馈由监听器显式登记。
     *
     * @param data 本次处理的输入数据
     */
    protected DiceEvent(T data) { super(data); }

    /**
     * 将监听器启动的异步业务登记到当前事件完成链，不改变 Core 事件的异步监听语义。
     *
     * @param stage 需要观察或组合的完成阶段
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public void includeCompletion(CompletionStage<Void> stage) {
        completion = completion.thenCombine(Objects.requireNonNull(stage), (first, second) -> null);
    }
}
