package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonType;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.JsonConstraint;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 人类表达观察的数据契约，目标由宿主固定，模型只能提出带原话的风格候选。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * <br>2026/11/06：独立与联合整理共用具名 JSON 对象合同。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
public final class StyleObservations {
    /** 禁止实例化契约容器。 */
    private StyleObservations() { }
    /**
     * 表达适用场景，不把针对某用户的请求推广到整个群。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.9.0-l
     */
    public enum Context { GENERAL, TECHNICAL, CASUAL }
/**
 * 待验证的表达模式，不包含基础身份、权限和采用指令。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：类型字段接入统一 JSON 结构描述。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param field 允许微调的表达字段
 * @param pattern 简洁表达特征
 * @param context 适用场景
 * @param quotes 真实用户原话
 * @param segments 对应中期片段
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
    // 2026/11/06：风格字段和证据要求由共用类型说明生成。
    public record Candidate(
            @Description("只从 styleTarget.fields 中选择表达字段。") @JsonConstraint(enumValues = {"speakingStyle", "speaking detail"}) String field,
            @Description("非空真实措辞、幽默或节奏模式，最多256字；不是用户要求机器人改变的指令。") @JsonConstraint(minLength = 1, maxLength = 256) String pattern,
            @Description("GENERAL 通用，TECHNICAL 技术，CASUAL 闲聊。") Context context,
            @Description("1至4条真实用户逐字引用，仅来自 styleTarget.evidenceIds。") @JsonConstraint(minItems = 1, maxItems = 4) List<LongMemories.Quote> quotes,
            @Description("1至4个本批真实片段 UUID，须包含所引用原话。") @JsonConstraint(minItems = 1, maxItems = 4) List<UUID> segments) {
        /**
         * 固定有界候选，关系、经历和个人偏好不能作为群体表达字段。
         * @param field 表达字段
         * @param pattern 模式正文
         * @param context 适用场景
         * @param quotes 原话引用
         * @param segments 片段来源
         * @throws IllegalArgumentException 当字段、来源或范围无效时
         */
        public Candidate {
            Objects.requireNonNull(context); quotes = List.copyOf(quotes); segments = List.copyOf(segments);
            if (!Set.of("speakingStyle", "speaking detail").contains(field) || pattern == null || pattern.isBlank() || pattern.length() > 256
                    || quotes.isEmpty() || quotes.size() > 4 || segments.isEmpty() || segments.size() > 4) {
                throw new IllegalArgumentException("表达观察字段、来源或容量无效。");
            }
            pattern = pattern.strip();
        }
    }
    /**
     * 宿主固定的学习目标，已有模式帮助增量观察保持命名一致。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param basis 当前人格身份及管理代次
     * @param fields 代码与配置共同允许的字段
     * @param evidenceIds 仍属于该人格的真实消息标识
     * @param known 已有模式摘要，不包含原话
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.9.0-l
     */
    public record Target(AgentPersonalitySnapshot basis, Set<String> fields, Set<String> evidenceIds, List<String> known) {
        /**
         * 冻结已复核目标，模型不能修改绑定或扩大白名单。
         * @param basis 人格快照
         * @param fields 合法字段
         * @param evidenceIds 合格原话标识
         * @param known 既有表达模式
         */
        public Target { Objects.requireNonNull(basis); fields = Set.copyOf(fields); evidenceIds = Set.copyOf(evidenceIds); known = List.copyOf(known); }
    }
/**
 * 一次模型同时返回事实与表达观察，两类候选分别验证和采用。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：类型字段接入统一 JSON 结构描述。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param memories 长期事实候选
 * @param styles 表达观察候选
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
    // 2026/11/06：独立事实与联合观察使用同一对象协议；无目标时 styles 必须为空。
    @LLMJsonType(maxCharacters = 16000)
    public record Analysis(
            @Description("值得长期保留的事实、偏好或共同经历，优先最多4项；无依据则空数组。") @JsonConstraint(maxItems = 4) List<LongMemories.Candidate> memories,
            @Description("最多2项表达观察；未提供 styleTarget 或没有真实依据时为空数组。") @JsonConstraint(maxItems = 2) List<Candidate> styles) {
        /**
         * 限制单批变化量，不允许生成器返回无界观察。
         * @param memories 长期候选
         * @param styles 表达候选
         * @throws IllegalArgumentException 当候选数量超过预算时
         */
        public Analysis {
            memories = List.copyOf(memories); styles = List.copyOf(styles);
            // 2026/11/06：提示、Schema 与 Java 统一为最多四条事实、两条风格。
            if (memories.size() > 4 || styles.size() > 2) { throw new IllegalArgumentException("联合整理候选超过预算。"); }
        }
    }
    /**
     * 当前 scope 的观察摘要，不导出原始发言或其他区域样本。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param field 表达字段
     * @param pattern 已观察模式
     * @param context 适用场景
     * @param messages 独立消息数
     * @param segments 独立片段数
     * @param authors 样本作者 UUID
     * @param adopted 是否已经采用
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.9.0-l
     */
    public record View(String field, String pattern, Context context, int messages, int segments, Set<UUID> authors, boolean adopted) { }
}
