package org.zexnocs.teanekoplugin.general.signin.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

/**
 * 一个签到区块的数据类。主要用于补签时提供签到区块的相关信息。
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
public class SignInChunkData {
    /// 查询该数据的 key
    @JsonIgnore
    public static final String KEY = "chunk";

    /**
     * 该签到块的最后一次签到日期。单位是毫秒。
     */
    @Builder.Default
    @JsonProperty("last_time")
    private Instant lastTime = Instant.EPOCH;

    /**
     * 兼容旧版本以毫秒时间戳保存的签到区块数据。
     *
     * @param lastTimeMs 最后签到时间的 Unix 毫秒时间戳
     */
    @JsonProperty("last_time_ms")
    private void setLegacyLastTimeMs(long lastTimeMs) {
        if (lastTime == null || Instant.EPOCH.equals(lastTime)) {
            lastTime = Instant.ofEpochMilli(lastTimeMs);
        }
    }

    /**
     * 该签到块的连续签到天数。
     * 第一天时间 = last_time - continuous + 1
     */
    @JsonProperty("continuous")
    private int continuous;
}
