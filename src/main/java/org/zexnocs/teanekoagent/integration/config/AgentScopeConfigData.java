package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 新 Agent 的作用域注册数据，只有真实登记记录才允许使用默认启用值。
 * 私有模型连接随组件原文受保护保存，普通查询脱敏，不提供补正 key 写入入口。
 *
 * <br>2026/11/03：新 scope 默认允许自动长期学习，已有显式关闭配置继续生效。
 * <br>2026/11/03：登记独立拟人表达开关。
 * <br>2026/11/03：登记独立素材采集开关，不绑定记忆学习。
 * <br>2026/11/03：分别控制共享图库回复和客户端原生表情。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：新增聊天模型路由，空值继承文件默认。
 * <br>2026/11/04：新登记只安装 LLM，旧记录保持原行为，业务参数从独立组件读取。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：新增默认开启的失败原因回复配置。
 *
 * <br>2026/11/06：以字段注解声明人格与表达长文本编辑器。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.4-l
 */
@Getter @Setter
public class AgentScopeConfigData implements IConfigData {
    // 2026/11/04：原配置保存版本化组件文本，普通查询不回显模型凭据。
    @org.zexnocs.teanekocore.framework.description.Mask("组件装配已隐藏，请在受保护的网页表单中查看和修改。")
    @Description("版本化 Agent 组件 JSON；必需 llm，其余组件显式安装。")
    private @org.jspecify.annotations.Nullable String components = org.zexnocs.teanekoagent.component.AgentAssembly.MINIMAL;

    /** 2026/11/04：新登记只安装 LLM，不默认拥有记忆等业务能力。 */
    public AgentScopeConfigData() {}

    /**
     * 2026/11/04：未包含装配字段的历史记录保留旧行为，不在读取时写回。
     * @param components 原装配文本，缺失表示历史记录
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public AgentScopeConfigData(@com.fasterxml.jackson.annotation.JsonProperty("components")
            @org.jspecify.annotations.Nullable String components) {
        this.components = components;
    }

    /** @return 2026/11/04：无秘密的运行装配，旧记录沿用原固定能力。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public org.zexnocs.teanekoagent.component.AgentAssembly assembly() {
        return components == null ? org.zexnocs.teanekoagent.component.AgentAssembly.legacyDefaults()
                : org.zexnocs.teanekoagent.component.AgentAssembly.parse(components);
    }

    /**
     * @param component 组件标识
     * @param field 参数字段
     * @param previous 历史参数值
     * @param initial 新组件独立默认值
     * @return 2026/11/04：组件开关，未安装时不能借旧字段开启能力
     */
    private boolean componentFlag(String component, String field, boolean previous, boolean initial) {
        var assembly = assembly();
        return assembly.legacy() ? previous : assembly.has(component) && assembly.settings(component).path(field).asBoolean(initial);
    }

    /**
     * @param component 组件标识
     * @param field 参数字段
     * @param previous 历史参数值
     * @param initial 新组件独立默认值
     * @return 2026/11/04：组件独立文本参数
     */
    private String componentText(String component, String field, String previous, String initial) {
        var assembly = assembly();
        return assembly.legacy() ? previous : !assembly.has(component) ? initial : assembly.settings(component).path(field).asString(initial);
    }
    @Description("是否启用当前作用域的 Agent 功能。")
    private boolean enabled = true;

    // 2026/11/05：默认向原聊天反馈可公开的失败原因，不包含凭据或模型原文。
    @Description("Agent 失败时是否通过原发送器回复原因，默认开启；关闭后仅记录后台诊断。示例：/cfg set agent failureReplyEnabled false。")
    private boolean failureReplyEnabled = true;

    // 2026/11/04：每个聊天独立选择已添加的模型路由，旧记录空值继承默认。
    @Description("使用的模型配置路由；留空继承 model.yml 默认模型。")
    private String modelRoute = "";
    // 2026/11/03：表达与记忆学习开关独立，旧记录缺省允许。
    @Description("是否使用人格驱动的拟人分段和发送节奏；关闭后保留原普通回复。")
    private boolean expressionEnabled = true;

    // 2026/11/03：已登记启用区域默认允许收集真实人类图片，仍执行本地限流。
    @Description("是否允许当前作用域主动/被动收集表情包；全实例共享图库，独立于记忆学习。")
    private boolean stickerCollectEnabled = true;
    // 2026/11/03：选择发送与采集独立，关闭采集仍可使用既有图库。
    @Description("是否允许当前作用域按本轮类别查询使用共享表情包；仍要求表达能力与客户端支持。")
    private boolean stickerReplyEnabled=true;
    @Description("是否允许当前作用域使用当前客户端明确提供的原生表情目录，不占表情包图库。")
    private boolean nativeExpressionsEnabled=true;

    @Description("是否向真实原生用户请求开放命令工具；仍执行原命令权限，默认关闭。")
    private boolean commandToolsEnabled = false;

    @Description("是否允许当前作用域按需加载本地 Skill，默认关闭；不授予额外工具权限。")
    private boolean skillsEnabled = false;
    @Description("显式允许的 Skill 名称，英文逗号分隔，最多八项；空值不选入任何 Skill。")
    private String skills = "";

    @Description("是否保存当前作用域的文本历史；采集不启动模型。")
    private boolean historyCaptureEnabled = true;
    @Description("是否允许模型查询当前作用域和有效绑定内的文本历史。")
    private boolean historyReadEnabled = true;

    @Description("是否自动压缩成功会话轮次；摘要独立于事实学习，默认开启。")
    private boolean autoSummaryEnabled = true;

    @Description("是否允许记忆检索、画像读取及记忆注入。")
    private boolean memoryReadEnabled = true;

    // 2026/11/03：新长期模块允许按版本维护本人条目，旧事实管理权限继续沿用原入口。
    @Description("是否允许明确新增本人事实及新长期条目的本人纠正/遗忘；不允许修改他人数据，旧事实管理仍须原管理权限。")
    private boolean memoryAddEnabled = true;

    // 2026/11/03：自动学习由持久中期片段触发，不要求用户逐条请求记住。
    @Description("是否允许从已采集的真实对话自动整理长期事实、偏好和用户画像；关闭不停止已授权的短期采集，也不删除现有记忆。")
    private boolean memoryLearningEnabled = true;

    @Description("基础人格 ID，空值使用本地 personality.yml 的默认人格。")
    private String personalityId = "";
    @Description("是否使用当前作用域的自定义人格描述。")
    private boolean customPersonalityEnabled = false;
    @Description("自定义人格描述，不改变运行权限；最多 4096 字符。")
    @org.zexnocs.teanekocore.framework.config.ValueEditor("LONG_TEXT")
    private String customPersonality = "";
    @Description("自定义说话风格；最多 4096 字符。")
    @org.zexnocs.teanekocore.framework.config.ValueEditor("LONG_TEXT")
    private String customSpeakingStyle = "";
    @Description("是否应用已接受的人格补正，独立于用户画像和记忆。")
    private boolean personalityCorrectionsEnabled = true;
    @Description("是否允许后台生成及接受人格表达补正，独立于补正应用和用户画像。")
    private boolean personalityLearningEnabled = true;

    @Description("活动模式：PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。")
    private String mode = AgentScopePolicy.Mode.PASSIVE_BASIC.name();

    @Description("自动入站的明确机器人称呼，英文逗号分隔，最多八项，每项1至32字符；空值不按称呼触发。")
    private String triggerNames = "";

    @Description("自然语言日历提醒的明确时区；空值要求用户在原文提供，不使用服务器默认时区。")
    private String taskTimeZone = "";

    @Description("主动触发静默时段的时区，默认明确使用 UTC，不跟随服务器时区。")
    private String activityTimeZone = "UTC";
    @Description("主动发言冷却秒数，默认十五分钟，范围一秒至一天。")
    private int proactiveCooldownSeconds = 900;
    @Description("ACTIVE 模式是否允许定时自主候选；不补跑错过的候选。")
    private boolean proactiveTimedEnabled = true;
    @Description("ACTIVE 模式是否允许对未触发被动回复的当前原生消息提出接话候选。")
    private boolean proactiveInterjectionEnabled = true;
    @Description("定时自主候选间隔秒数，范围一分钟至一天，默认十五分钟。")
    private int proactiveIntervalSeconds = 900;
    @Description("自主候选尝试退避秒数，弃言或失败同样适用，范围一分钟至一天。")
    private int proactiveAttemptCooldownSeconds = 60;
    @Description("主动触发静默起点小时，默认23；与终点相同表示关闭静默。")
    private int proactiveQuietStartHour = 23;
    @Description("主动触发静默终点小时，默认7；跨日区间按显式时区计算。")
    private int proactiveQuietEndHour = 7;

    @Description("短期历史消息上限，0 关闭窗口，-1 使用本地默认值；不拆开完整工具轮次。")
    private int historyMaxMessages = -1;

    @Description("统一协议 JSON 输入字节上限，必须为正，-1 使用本地默认值。")
    private long inputMaxBytes = -1;

    /** @return 2026/11/04：memory 组件的 historyCaptureEnabled 参数，未安装时关闭。 */
    public boolean isHistoryCaptureEnabled() {
        return componentFlag("memory", "historyCaptureEnabled", historyCaptureEnabled, true);
    }

    /** @return 2026/11/04：memory 组件的 historyReadEnabled 参数，未安装时关闭。 */
    public boolean isHistoryReadEnabled() {
        return componentFlag("memory", "historyReadEnabled", historyReadEnabled, true);
    }

    /** @return 2026/11/04：memory 组件的 autoSummaryEnabled 参数，未安装时关闭。 */
    public boolean isAutoSummaryEnabled() {
        return componentFlag("memory", "autoSummaryEnabled", autoSummaryEnabled, true);
    }

    /** @return 2026/11/04：memory 组件的 memoryReadEnabled 参数，未安装时关闭。 */
    public boolean isMemoryReadEnabled() {
        return componentFlag("memory", "memoryReadEnabled", memoryReadEnabled, true);
    }

    /** @return 2026/11/04：memory 组件的 memoryAddEnabled 参数，未安装时关闭。 */
    public boolean isMemoryAddEnabled() {
        return componentFlag("memory", "memoryAddEnabled", memoryAddEnabled, true);
    }

    /** @return 2026/11/04：memory 组件的 memoryLearningEnabled 参数，未安装时关闭。 */
    public boolean isMemoryLearningEnabled() {
        return componentFlag("memory", "memoryLearningEnabled", memoryLearningEnabled, true);
    }

    /** @return 2026/11/04：personality 组件的 customPersonalityEnabled 参数，未安装时关闭。 */
    public boolean isCustomPersonalityEnabled() {
        return componentFlag("personality", "customPersonalityEnabled", customPersonalityEnabled, false);
    }

    /** @return 2026/11/04：personality 组件的 personalityCorrectionsEnabled 参数，未安装时关闭。 */
    public boolean isPersonalityCorrectionsEnabled() {
        return componentFlag("personality", "personalityCorrectionsEnabled", personalityCorrectionsEnabled, true);
    }

    /** @return 2026/11/04：personality 组件的 personalityLearningEnabled 参数，未安装时关闭。 */
    public boolean isPersonalityLearningEnabled() {
        return componentFlag("personality", "personalityLearningEnabled", personalityLearningEnabled, true);
    }

    /** @return 2026/11/04：skill 组件的 skillsEnabled 参数，未安装时关闭。 */
    public boolean isSkillsEnabled() {
        return componentFlag("skill", "skillsEnabled", skillsEnabled, true);
    }

    /** @return 2026/11/04：tool 组件的 commandToolsEnabled 参数，未安装时关闭。 */
    public boolean isCommandToolsEnabled() {
        return componentFlag("tool", "commandToolsEnabled", commandToolsEnabled, false);
    }

    /** @return 2026/11/04：sticker 组件的 stickerCollectEnabled 参数，未安装时关闭。 */
    public boolean isStickerCollectEnabled() {
        return componentFlag("sticker", "stickerCollectEnabled", stickerCollectEnabled, true);
    }

    /** @return 2026/11/04：sticker 组件的 stickerReplyEnabled 参数，未安装时关闭。 */
    public boolean isStickerReplyEnabled() {
        return componentFlag("sticker", "stickerReplyEnabled", stickerReplyEnabled, true);
    }

    /** @return 2026/11/04：expression 组件的 expressionEnabled 参数，未安装时关闭。 */
    public boolean isExpressionEnabled() {
        return componentFlag("expression", "expressionEnabled", expressionEnabled, true);
    }

    /** @return 2026/11/04：expression 组件的 nativeExpressionsEnabled 参数，未安装时关闭。 */
    public boolean isNativeExpressionsEnabled() {
        return componentFlag("expression", "nativeExpressionsEnabled", nativeExpressionsEnabled, true);
    }

    /** @return 2026/11/04：proactive 组件的 proactiveTimedEnabled 参数，未安装时关闭。 */
    public boolean isProactiveTimedEnabled() {
        return componentFlag("proactive", "proactiveTimedEnabled", proactiveTimedEnabled, true);
    }

    /** @return 2026/11/04：proactive 组件的 proactiveInterjectionEnabled 参数，未安装时关闭。 */
    public boolean isProactiveInterjectionEnabled() {
        return componentFlag("proactive", "proactiveInterjectionEnabled", proactiveInterjectionEnabled, true);
    }

    /** @return 2026/11/04：personality 组件的 personalityId 独立参数。 */
    public String getPersonalityId() {
        return componentText("personality", "personalityId", personalityId, "");
    }

    /** @return 2026/11/04：personality 组件的 customPersonality 独立参数。 */
    public String getCustomPersonality() {
        return componentText("personality", "customPersonality", customPersonality, "");
    }

    /** @return 2026/11/04：personality 组件的 customSpeakingStyle 独立参数。 */
    public String getCustomSpeakingStyle() {
        return componentText("personality", "customSpeakingStyle", customSpeakingStyle, "");
    }

    /** @return 2026/11/04：skill 组件的 skills 独立参数。 */
    public String getSkills() {
        return componentText("skill", "skills", skills, "");
    }

    /** @return 2026/11/04：记忆组件的窗口上限；未安装时不保留上下文。 */
    public int getHistoryMaxMessages() {
        var assembly = assembly();
        return assembly.legacy() ? historyMaxMessages : !assembly.has("memory") ? 0
                : assembly.settings("memory").path("historyMaxMessages").asInt(-1);
    }

    /** @return 2026/11/04：LLM 组件所选系统路由，私有配置由受保护端口解析。 */
    public String getModelRoute() {
        return assembly().legacy() ? modelRoute : assembly().settings("llm").path("route").asString("");
    }

    /**
     * 为已存在的登记记录建立不可变策略，非法旧数据不能自动改为启用。
     * @return 已登记作用域策略
     * @throws IllegalArgumentException 当模式值无法识别时
     */
    public AgentScopePolicy toPolicy() {
        // 2026/11/04：模型路由与开关一起固定到准入策略。
        try { return new AgentScopePolicy(true, enabled, AgentScopePolicy.Mode.valueOf(mode), getModelRoute(), assembly()); }
        catch (IllegalArgumentException | NullPointerException ignored) {
            throw new IllegalArgumentException("Agent 活动模式或模型路由配置无效，请检查对应字段。");
        }
    }

    /**
     * @param field 主动活动参数名
     * @param previous 旧配置值
     * @param initial 独立默认值
     * @return 2026/11/04：本组件当前固定数值
     */
    private int proactiveNumber(String field, int previous, int initial) {
        var value = assembly();
        return value.legacy() ? previous : value.settings("proactive").path(field).asInt(initial);
    }

    /** @return 2026/11/04：提醒组件的独立时区。 */
    public String getTaskTimeZone() {
        return componentText("schedule", "taskTimeZone", taskTimeZone, "");
    }

    /** @return 2026/11/04：主动活动组件的独立时区。 */
    public String getActivityTimeZone() {
        return componentText("proactive", "activityTimeZone", activityTimeZone, "UTC");
    }

    /** @return 2026/11/04：主动组件的 proactiveCooldownSeconds 参数。 */
    public int getProactiveCooldownSeconds() {
        return proactiveNumber("proactiveCooldownSeconds", proactiveCooldownSeconds, 900);
    }

    /** @return 2026/11/04：主动组件的 proactiveIntervalSeconds 参数。 */
    public int getProactiveIntervalSeconds() {
        return proactiveNumber("proactiveIntervalSeconds", proactiveIntervalSeconds, 900);
    }

    /** @return 2026/11/04：主动组件的 proactiveAttemptCooldownSeconds 参数。 */
    public int getProactiveAttemptCooldownSeconds() {
        return proactiveNumber("proactiveAttemptCooldownSeconds", proactiveAttemptCooldownSeconds, 60);
    }

    /** @return 2026/11/04：主动组件的 proactiveQuietStartHour 参数。 */
    public int getProactiveQuietStartHour() {
        return proactiveNumber("proactiveQuietStartHour", proactiveQuietStartHour, 23);
    }

    /** @return 2026/11/04：主动组件的 proactiveQuietEndHour 参数。 */
    public int getProactiveQuietEndHour() {
        return proactiveNumber("proactiveQuietEndHour", proactiveQuietEndHour, 7);
    }
}
