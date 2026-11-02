package org.zexnocs.teanekoagent.adapter.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 新 Agent 的作用域注册数据，只有真实登记记录才允许使用默认启用值。
 * 不提供认证、连接或补正 key 的普通 cfg 写入入口。
 *
 * <br>2026/11/03：新 scope 默认允许自动长期学习，已有显式关闭配置继续生效。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Getter @Setter @NoArgsConstructor
public class AgentScopeConfigData implements IConfigData {
    @Description("是否启用当前作用域的 Agent 功能。")
    private boolean enabled = true;

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
    private String customPersonality = "";
    @Description("自定义说话风格；最多 4096 字符。")
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

    /**
     * 为已存在的登记记录建立不可变策略，非法旧数据不能自动改为启用。
     * @return 已登记作用域策略
     * @throws IllegalArgumentException 当模式值无法识别时
     */
    public AgentScopePolicy toPolicy() {
        try { return new AgentScopePolicy(true, enabled, AgentScopePolicy.Mode.valueOf(mode)); }
        catch (IllegalArgumentException | NullPointerException ignored) {
            throw new IllegalArgumentException("Agent 活动模式配置无效，请使用已声明的模式值。");
        }
    }
}
