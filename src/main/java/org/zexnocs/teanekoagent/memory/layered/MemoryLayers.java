package org.zexnocs.teanekoagent.memory.layered;

import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.utils.StableDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 分层记忆的不可变数据契约，来源和处理覆盖与生成正文分开保存。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class MemoryLayers {
    /** 禁止实例化只提供数据契约的类型。 */
    private MemoryLayers() { }
    /**
     * 按稳定作用域及空间引用分区，人格变化不丢失同一分区的原始证据。
     * @param reference 原授权引用
     * @return 不含原始身份文字的稳定分区
     */
    public static String stream(MemoryNamespaceReference reference) {
        return StableDigest.sha256("memory-stream-v1", reference.scope().clientId(), reference.scope().scopeId(),
                reference.namespaceId().value(), Long.toString(reference.bindingRevision()), Long.toString(reference.generation()));
    }
    /**
     * 已提交的短期消息，序号表示接收顺序，事件时间表示实际发生时间。
     * @param sequence 分区内递增序号
     * @param message 原始文本及真实来源
     * @param personality 采集时人格身份，仅供追溯
     * @param expiresAt 原保留期，不因重投延长
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Message(long sequence, HistoryMessage message, String personality, Instant expiresAt) {
        /**
         * 要求稳定序号和完整来源。
         * @param sequence 稳定序号
         * @param message 原消息
         * @param personality 原人格
         * @param expiresAt 到期时间
         * @throws IllegalArgumentException 当序号非正时
         */
        public Message {
            if (sequence < 1) { throw new IllegalArgumentException("短期记忆序号无效。"); }
            Objects.requireNonNull(message); Objects.requireNonNull(personality); Objects.requireNonNull(expiresAt);
        }
    }
    /**
     * 可恢复目录项，实际读取仍核对当前空间及配置。
     * @param id 稳定分区
     * @param reference 原始引用
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Stream(String id, MemoryNamespaceReference reference) {
        /**
         * 拒绝目录标识与真实来源错位。
         * @param id 持久分区标识
         * @param reference 原始引用
         * @throws IllegalArgumentException 当分区与引用不匹配时
         */
        public Stream {
            Objects.requireNonNull(reference);
            if (!MemoryLayers.stream(reference).equals(id)) { throw new IllegalArgumentException("分层记忆目录与来源不匹配。"); }
        }
    }
    /**
     * 已取得租约的连续压缩输入，模型无权指定源范围。
     * @param stream 原分区
     * @param leaseId 此次领取令牌
     * @param through 已固定的覆盖上界
     * @param fingerprint 实际输入指纹
     * @param messages 不可变消息前缀
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Batch(Stream stream, UUID leaseId, long through, String fingerprint, List<Message> messages) {
        /**
         * 固定租约及真实消息，禁止空批次。
         * @param stream 来源分区
         * @param leaseId 领取令牌
         * @param through 覆盖上界
         * @param fingerprint 输入指纹
         * @param messages 连续输入
         * @throws IllegalArgumentException 当输入为空、超限、范围不连续或来源错位时
         */
        public Batch {
            Objects.requireNonNull(stream); Objects.requireNonNull(leaseId); Objects.requireNonNull(fingerprint);
            messages = List.copyOf(messages);
            if (messages.isEmpty() || messages.size() > 64 || through != messages.getLast().sequence()) { throw new IllegalArgumentException("记忆压缩覆盖无效。"); }
            long expected = messages.getFirst().sequence();
            for (var message : messages) {
                if (message.sequence() != expected++ || !message.message().source().scope().equals(stream.reference().scope())) {
                    throw new IllegalArgumentException("记忆压缩消息不连续或来源错位。");
                }
            }
        }
    }
    /**
     * 模型只生成有界内容，来源、时间和水位由宿主确定。
     * @param brief 简短摘要
     * @param details 完整片段摘要，保留决定、纠正、重要日期及待办
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Digest(String brief, String details) {
        /**
         * 拒绝空摘要和超过既定容量的生成结果。
         * @param brief 简短摘要
         * @param details 详细摘要
         * @throws IllegalArgumentException 当摘要为空或超出容量时
         */
        public Digest {
            if (brief == null || brief.isBlank() || brief.length() > 600 || details == null || details.isBlank() || details.length() > 4000) {
                throw new IllegalArgumentException("中期记忆摘要为空或超过容量。");
            }
        }
    }
    /**
     * 持久中期片段，事件范围与生成时刻分开，普通召回使用模糊时间提示。
     * @param id 稳定片段标识
     * @param stream 固定来源
     * @param from 覆盖起始序号
     * @param through 覆盖末尾序号
     * @param fingerprint 原输入指纹
     * @param digest 有界摘要
     * @param occurredFrom 原消息最早事件时间
     * @param occurredThrough 原消息最晚事件时间
     * @param createdAt 实际提交处理时刻
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Segment(UUID id, Stream stream, long from, long through, String fingerprint, Digest digest,
                          Instant occurredFrom, Instant occurredThrough, Instant createdAt) {
        /**
         * 固定生成结果的来源和时间依据，不允许倒置覆盖或事件时间。
         * @param id 稳定片段标识
         * @param stream 固定来源
         * @param from 起始序号
         * @param through 结束序号
         * @param fingerprint 原输入指纹
         * @param digest 有界候选
         * @param occurredFrom 最早事件时间
         * @param occurredThrough 最晚事件时间
         * @param createdAt 实际生成时间
         * @throws IllegalArgumentException 当范围或时间倒置时
         */
        public Segment {
            Objects.requireNonNull(id); Objects.requireNonNull(stream); Objects.requireNonNull(fingerprint); Objects.requireNonNull(digest);
            Objects.requireNonNull(occurredFrom); Objects.requireNonNull(occurredThrough); Objects.requireNonNull(createdAt);
            if (from < 1 || through < from || occurredFrom.isAfter(occurredThrough)) { throw new IllegalArgumentException("中期记忆覆盖或事件时间无效。"); }
        }
    }
    /**
     * 本次有界近期读取，水位不代表整个分区已经读完。
     * @param recent 最近原始消息
     * @param segments 已提交中期片段
     * @param compressedThrough 已提交的压缩水位
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record View(List<Message> recent, List<Segment> segments, long compressedThrough) {
        /**
         * 冻结读取视图，不向组件暴露存储可变容器。
         * @param recent 最近消息
         * @param segments 中期片段
         * @param compressedThrough 原压缩水位
         */
        public View { recent = List.copyOf(recent); segments = List.copyOf(segments); }
    }
}
