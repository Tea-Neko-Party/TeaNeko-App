package org.zexnocs.teanekoclient.onebot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoclient.onebot.state.OnebotState;

/**
 * 关于 onebot 机器人状态的配置
 *
 * @see OnebotState
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.3.2
 */
@Setter
@Getter
@NoArgsConstructor
public class OnebotStateConfig {
    /**
     * 初始状态
     * @see OnebotState
     */
    @JsonProperty("initial-state")
    private OnebotState initialState = OnebotState.DEFAULT;
}
