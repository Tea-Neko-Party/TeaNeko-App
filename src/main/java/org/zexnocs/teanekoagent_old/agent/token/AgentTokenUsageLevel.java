package org.zexnocs.teanekoagent_old.agent.token;

/**
 * Agent token 使用级别。
 * <br>用于区分普通记录、需要提醒的记录和异常记录。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
public enum AgentTokenUsageLevel {
    /**
     * 普通 token 使用。
     */
    NORMAL,

    /**
     * 接近配置上限或达到 warning 阈值的 token 使用。
     */
    WARNING,

    /**
     * 达到异常阈值、上下文接近耗尽或模型调用出现异常。
     */
    ABNORMAL
}
