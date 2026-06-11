package org.zexnocs.teanekoplugin.general.info.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 个人简介数据。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalInfoData {
    @JsonProperty("personal_info")
    private String personalInfo;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * 兼容旧版本以毫秒时间戳保存的更新时间。
     *
     * @param time Unix 毫秒时间戳
     */
    @JsonProperty("time")
    private void setLegacyTime(long time) {
        if (updatedAt == null) {
            updatedAt = Instant.ofEpochMilli(time);
        }
    }
}
