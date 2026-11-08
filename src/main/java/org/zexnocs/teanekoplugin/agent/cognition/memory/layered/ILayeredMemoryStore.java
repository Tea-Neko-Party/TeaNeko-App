package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 分层记忆事务端口，目录、压缩租约与水位均来自持久状态。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：提供不混用中期保留策略的近期原消息查询端口。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
public interface ILayeredMemoryStore {
    /**
     * 有界遍历可恢复的分区目录，空页后从头继续。
     * @param after 排除的目录游标
     * @return 有界稳定目录
     */
    CompletionStage<List<MemoryLayers.Stream>> streams(String after);
    /**
     * 原子领取未处理前缀，租约失效后允许重启任务重新领取。
     * @param stream 原分区
     * @param settings 本次工作策略
     * @param now 领取时间
     * @return 可处理输入，未达到触发条件或租约仍有效时为空
     */
    CompletionStage<Optional<MemoryLayers.Batch>> claim(MemoryLayers.Stream stream, LayeredMemorySettings settings, Instant now);
    /**
     * 同事务保存片段和覆盖水位，旧租约或旧代次不得提交。
     * @param batch 原输入与租约
     * @param digest 已验收生成结果
     * @param now 当前时刻
     * @return 成功提交时为真
     */
    CompletionStage<Boolean> commit(MemoryLayers.Batch batch, MemoryLayers.Digest digest, Instant now);
    /**
     * 释放本次租约并保存退避，不推进覆盖水位。
     * @param batch 原领取输入
     * @param now 失败观察时间
     * @return 退避提交完成阶段
     */
    CompletionStage<Void> retry(MemoryLayers.Batch batch, Instant now);
    /**
     * 读取同一作用域近期消息与中期片段，不进行模型调用。
     * @param reference 当前授权引用
     * @param now 查询时刻
     * @param retention 中期保留期
     * @return 受条数与原有效期限制的视图
     */
    CompletionStage<MemoryLayers.View> read(MemoryNamespaceReference reference, Instant now, java.time.Duration retention);

    /**
     * 2026/11/06：只需近期互动的组件使用原消息有效期，不混用中期保留策略。
     * @param reference 当前授权引用
     * @param now 查询时间
     * @return 有界且未过期的近期原消息
     */
    default CompletionStage<List<MemoryLayers.Message>> recent(MemoryNamespaceReference reference, Instant now) {
        return read(reference, now, java.time.Duration.ZERO).thenApply(MemoryLayers.View::recent);
    }
}
