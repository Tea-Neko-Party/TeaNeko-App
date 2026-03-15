package org.zexnocs.teanekoclient.onebot.state;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.zexnocs.teanekocore.framework.state.IState;

/**
 * onebot 机器人的状态列表
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.3.2
 */
public enum OnebotState implements IState {
    /**
     * 默认状态；
     * 打开所有的 command、sender 发送器。
     */
    @JsonProperty("default")
    DEFAULT,

    /**
     * STOP 状态；
     * 只有切换状态的指令允许使用。
     */
    @JsonProperty("stop")
    STOP,

    /**
     * DEBUG 状态；
     * 只允许 debugger 使用指令。
     */
    @JsonProperty("debug")
    DEBUG,

    /**
     * LLM 状态；
     * 允许 LLM 对话；
     * 除了 debugger 以外不允许使用指令。
     */
    @JsonProperty("llm")
    LLM,
}
