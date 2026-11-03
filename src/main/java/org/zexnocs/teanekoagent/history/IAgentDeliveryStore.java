package org.zexnocs.teanekoagent.history;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 逐段平台回执端口，区分发送前占用、真实平台确认和不确定结果，不承担自动补发。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
public interface IAgentDeliveryStore {
    /**
     * 固定一段内容与目标，只有首次占用者允许开始外部发送。
     * @param part 宿主固定的待发送段
     * @return 首次占用为真，原段已确认时为假，未决或冲突通过失败阶段返回
     */
    CompletionStage<Boolean> begin(Part part);
    /**
     * 记录真实结果；确认不可回退为不确定，失败不能触发重发。
     * @param part 原占用内容
     * @param acknowledged 是否实际平台回执成功
     * @param now 观察时间
     * @return 实际提交阶段
     */
    CompletionStage<Void> finish(Part part, boolean acknowledged, Instant now);
    /**
     * 有界查询当前固定分区的确认内容，不跨 scope 导出原话。
     * @param reference 原空间和 scope
     * @param requests 最多二十四个原请求
     * @param now 当前时刻
     * @return 请求到已确认内容的映射
     */
    CompletionStage<Map<UUID, View>> read(MemoryNamespaceReference reference, Set<UUID> requests, Instant now);
    /**
     * 单段发送占用；text 是有界实际文本或图片占位，digest 校验完整内容。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param reference 固定空间
     * @param actor 真实发起或系统身份
     * @param requestId 原请求
     * @param index 零起始段号
     * @param total 总段数
     * @param digest 完整内容摘要
     * @param text 有界文本
     * @param at 占用时刻
     * @param expiresAt 保留截止
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    record Part(MemoryNamespaceReference reference, AgentActorContext actor, UUID requestId, int index, int total,
                String digest, String text, Instant at, Instant expiresAt) {
        /**
         * 限制可信宿主边界，不允许模型改变发送目标。
         * @param reference 固定空间
         * @param actor 真实身份
         * @param requestId 原请求
         * @param index 段号
         * @param total 总段数
         * @param digest 原内容摘要
         * @param text 实际正文投影
         * @param at 占用时刻
         * @param expiresAt 保留截止
         * @throws IllegalArgumentException 当范围或目标不一致时
         */
        public Part {
            if (!actor.scope().equals(reference.scope()) || index < 0 || total < 1 || total > 32 || index >= total
                    || digest.isBlank() || text.length() > 4096 || !expiresAt.isAfter(at)) { throw new IllegalArgumentException("平台发送段的来源或范围无效。"); }
        }
    }
    /**
     * 平台回执摘要，确认不表示收件人已经阅读。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param state ACKNOWLEDGED、PARTIAL 或 UNKNOWN
     * @param confirmed 已确认段数
     * @param total 原总段数
     * @param text 仅已确认部分的文本投影
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    record View(String state, int confirmed, int total, String text) { }
}
