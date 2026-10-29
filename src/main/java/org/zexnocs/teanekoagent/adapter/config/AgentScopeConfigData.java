package org.zexnocs.teanekoagent.adapter.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 新 Agent 的作用域注册数据，只有真实登记记录才允许使用默认启用值。
 * 不承接旧人格配置中的认证、连接或补正字段。
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
