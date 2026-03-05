package org.zexnocs.teanekoplugin.general.signin.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 签到总体数据。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.1.0
 */
@Setter
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SignInData {
    /// 查询该数据的 key
    @JsonIgnore
    public static final String KEY = "data";

    /// 签到总天数
    @JsonProperty("total_days")
    private int totalDays;

    /// 最新一次签到的时间
    @JsonProperty("last_time_ms")
    private long lastTimeMs;

    /// 最新的一次签到幸运数字
    @JsonProperty("last_number")
    private int lastNumber;

    /// 当前连续签到天数
    @JsonProperty("continuous")
    private int continuousDays;
}
