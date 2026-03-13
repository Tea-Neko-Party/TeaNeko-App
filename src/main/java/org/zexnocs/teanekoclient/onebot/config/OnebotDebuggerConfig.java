package org.zexnocs.teanekoclient.onebot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * onebot debugger 配置
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.1
 */
@Setter
@Getter
public class OnebotDebuggerConfig {
    /// debugger 的 onebot ID
    @JsonProperty("debugger-id")
    private long debuggerId = -1;
}
