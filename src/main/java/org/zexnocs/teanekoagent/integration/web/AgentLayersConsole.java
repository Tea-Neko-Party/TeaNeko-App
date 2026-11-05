package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.memory.layered.LongMemoryJobs;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 三层记忆的受限查询与长期维护合同，不输出内部原话证据或压缩租约。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：展示后台整理状态并提供版本化暂停恢复。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.3-l
 */
public final class AgentLayersConsole {
    /** 禁止实例化契约容器。 */
    private AgentLayersConsole() {}

    /**
     * 查询不触发压缩、学习或新绑定。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * <br>2026/11/05：新增 REPLAY 和 REPLAY_OPERATION，沿用固定空间和操作回执。
     * @param action READ、EDIT、FORGET、OPERATION、REPLAY 或 REPLAY_OPERATION
     * @param namespace 空串使用当前绑定
     * @param generation 原代次，首次读取为 -1
     * @param subjectId 可选长期主体过滤
     * @param topic 可选字面话题
     * @param id 长期记录标识
     * @param revision 原内容版本
     * @param operationId 原操作编号
     * @param text 可选修正正文
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.3-l
     */
    public record Query(String action, String namespace, long generation, @Nullable UUID subjectId, String topic,
            @Nullable UUID id, long revision, @Nullable UUID operationId, @Nullable String text) {
        /**
         * @param action 明确动作
         * @param namespace 空间
         * @param generation 原代次
         * @param subjectId 过滤主体
         * @param topic 过滤话题
         * @param id 记录
         * @param revision 原版本
         * @param operationId 原操作
         * @param text 新正文
         * @throws IllegalArgumentException 当动作或参数范围无效时
         */
        public Query {
            // 2026/11/05：恢复只接受已读取的作业版本，不混用长期条目版本。
            if (!Set.of("READ", "EDIT", "FORGET", "OPERATION", "REPLAY", "REPLAY_OPERATION").contains(action) || namespace == null
                    || namespace.length() > 128 || generation < -1 || topic == null || topic.length() > 1024
                    || text != null && (text.isBlank() || text.length() > 512)
                    || !action.equals("READ") && (operationId == null || namespace.isBlank() || generation < 0)
                    || action.equals("EDIT") && (id == null || text == null || revision < 0)
                    || action.equals("FORGET") && (id == null || text != null || revision < 0)
                    || action.equals("REPLAY") && (revision < 0 || id != null || text != null)) {
                throw new IllegalArgumentException("分层记忆管理参数无效。");
            }
        }
    }

    /**
     * 当前聊天近期消息的纯文本投影，助手存档不代表已经发出。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param sequence 接收顺序
     * @param role 原角色
     * @param text 有界正文
     * @param at 来源时间
     * @param estimated 时间是否估算
     * @param truncated 是否省略尾部
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record Recent(long sequence, String role, String text, Instant at, boolean estimated, boolean truncated) {}

    /**
     * 持久中期摘要和来源时间区间。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 摘要标识
     * @param brief 简述
     * @param details 有界详情
     * @param from 最早来源时间
     * @param through 最晚来源时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record Episode(UUID id, String brief, String details, Instant from, Instant through) {}

    /**
     * 近期与中期按真实聊天隔离，长期沿原 namespace 共享。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param namespace 空间
     * @param generation 数据代次
     * @param recent 最近至多二十四条消息
     * @param episodes 最近至多六份中期摘要
     * @param compressedThrough 原压缩水位
     * @param longTerm 原有界长期召回，不冒充全量目录
     * @param consolidation 当前分区整理状态，空间尚不存在时为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.3-l
     */
    public record View(String namespace, long generation, List<Recent> recent, List<Episode> episodes,
                       long compressedThrough, LongMemories.Result longTerm, LongMemoryJobs.@Nullable Status consolidation) {}
}
