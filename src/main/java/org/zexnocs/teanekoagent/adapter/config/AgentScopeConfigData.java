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
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter @Setter @NoArgsConstructor
public class AgentScopeConfigData implements IConfigData {
    @Description("是否启用当前作用域的 Agent 功能。")
    private boolean enabled = true;

    @Description("是否允许记忆检索、画像读取及记忆注入。")
    private boolean memoryReadEnabled = true;

    @Description("是否允许用户明确请求新增自己的事实；不包含维护、导出或其他主体写入。")
    private boolean memoryAddEnabled = true;

    @Description("独立记忆学习策略开关；当前尚无自动学习执行器，不能代替明确新增意图或人格补正开关。")
    private boolean memoryLearningEnabled = false;

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
    @Description("是否接受人格表达补正候选；当前不自动发起学习模型调用。")
    private boolean personalityLearningEnabled = true;

    @Description("活动模式：PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。")
    private String mode = AgentScopePolicy.Mode.PASSIVE_BASIC.name();

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
