package org.zexnocs.teanekoagent.component.memory.fact;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 可信宿主的事实追加服务，固定记录时刻并等待存储提交，不承担聊天授权或画像生成。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public final class MemoryFacts {
    private final IMemoryFactStore store;
    private final Clock clock;
    /**
     * 保存业务端口和可信时钟，构造不访问存储。
     * @param store 可替换事务存储
     * @param clock 记录时刻来源
     * @throws NullPointerException 当依赖为空时
     */
    public MemoryFacts(IMemoryFactStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "事实存储端口不能为空。"); this.clock = Objects.requireNonNull(clock, "事实记录时钟不能为空。");
    }
    /**
     * 为一次追加固定时刻，重试仍须使用原操作 ID、引用和来源；不在这里重放工具动作。
     * @param command 宿主已经授权的明确追加意图
     * @return 真实提交后的不可变结果
     * @throws NullPointerException 当命令或端口阶段为空时
     */
    public CompletionStage<MemoryFactReceipt> append(MemoryFactAppend command) {
        return Objects.requireNonNull(store.append(Objects.requireNonNull(command, "事实追加命令不能为空。"), clock.instant()), "事实存储阶段不能为空。");
    }
}
