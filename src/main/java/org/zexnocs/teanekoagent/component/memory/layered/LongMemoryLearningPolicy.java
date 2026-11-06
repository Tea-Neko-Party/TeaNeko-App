package org.zexnocs.teanekoagent.component.memory.layered;

import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 长期提取前的有界批处理及信息质量规则，不改变事实证据的事务校验。
 * <br>2026/11/06：纯社交重复与有效短事实分开处理，版本参与提取幂等键。
 *
 * @param batchSegments 触发提取的片段数
 * @param maxWait 最早片段允许等待的时间
 * @param filterLowInformation 是否过滤纯重复社交消息
 * @param urgentEvents 是否允许明确重要事件提前提取
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public record LongMemoryLearningPolicy(int batchSegments, Duration maxWait,
                                       boolean filterLowInformation, boolean urgentEvents) {
    public static final String PROMPT_VERSION = "long-learning-2";
    public static final String SCHEMA_VERSION = "analysis-2";
    private static final List<String> SOCIAL = List.of("早上好", "晚上好", "hello", "你好", "您好", "哈喽",
            "早安", "晚安", "午安", "hey", "hi", "好哒", "好的", "收到", "谢谢", "感谢", "拜拜", "再见",
            "嗨", "嘿", "早", "哈", "呵", "嗯", "哦", "噢", "喵", "啊", "呀", "呢", "哟", "啦");
    private static final Pattern IMPORTANT = Pattern.compile(
            "^(?:请记住|记住|更正|纠正|从今天起|我决定|我(?:已|已经)?(?:毕业|结婚|离职|入职|搬家|确诊))"
                    + "|(?:今天|明天|下周|下个月).{0,24}(?:手术|面试|考试|婚礼|入职|搬家)"
                    + "|(?:截止日期|生日是|以后请叫我)");

    /**
     * 拒绝无界或不能及时完成的策略。
     * @param batchSegments 一至四个片段
     * @param maxWait 一秒至一天
     * @param filterLowInformation 低信息过滤
     * @param urgentEvents 事件提前触发
     * @throws IllegalArgumentException 当策略越界时
     */
    public LongMemoryLearningPolicy {
        if (batchSegments < 1 || batchSegments > 4 || maxWait == null || maxWait.isZero()
                || maxWait.isNegative() || maxWait.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("长期提取批次必须为1至4个片段，最大等待必须大于零且不超过一天。");
        }
    }

    /** @return 默认四片段聚合，最迟十分钟，明确重要事件可提前 */
    public static LongMemoryLearningPolicy defaults() {
        return new LongMemoryLearningPolicy(4, Duration.ofMinutes(10), true, true);
    }

    /**
     * 仅排除全部由问候、应答或标点构成的消息；短事实、否定及更正不会按长度丢弃。
     * @param message 原真实消息
     * @return 是否含可用于长期提取的新信息
     */
    public boolean informative(HistoryMessage message) {
        if (!filterLowInformation) {
            return true;
        }
        var normalized = Normalizer.normalize(message.text(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\p{Z}\\s]+", "");
        if (normalized.isBlank()) {
            return false;
        }
        // 长串重复问候用线性游标识别，避免嵌套重复正则回溯或栈溢出。
        int offset = 0;
        while (offset < normalized.length()) {
            String matched = null;
            for (var word : SOCIAL) {
                if (normalized.startsWith(word, offset)) {
                    matched = word;
                    break;
                }
            }
            if (matched == null) {
                return true;
            }
            offset += matched.length();
        }
        return false;
    }

    /**
     * 批次大小、最早片段等待和明确事件任一满足时提取；空证据可直接确认跳过。
     * @param segments 当前连续片段
     * @param evidence 过滤后的新证据
     * @param now 处理时间
     * @return 是否可以领取
     */
    public boolean ready(List<MemoryLayers.Segment> segments, List<LongMemories.Evidence> evidence, Instant now) {
        return evidence.isEmpty() || segments.size() >= batchSegments
                || !segments.getFirst().createdAt().plus(maxWait).isAfter(now)
                || urgentEvents && evidence.stream().anyMatch(value -> IMPORTANT.matcher(value.message().text()).find());
    }

    /**
     * 同一真实来源即使重新分段也只学习一次，内容变化不能被旧键吞掉。
     * @param evidence 宿主验证的证据
     * @return 不含正文的稳定摘要
     */
    public static String evidenceKey(LongMemories.Evidence evidence) {
        var message = evidence.message();
        return StableDigest.sha256(message.source().scope().clientId(), message.source().scope().scopeId(),
                message.source().userId().orElseThrow().toString(), message.messageId(), message.text());
    }
}
