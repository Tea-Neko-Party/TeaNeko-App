package org.zexnocs.teanekoplugin.general.info.personal;

import org.zexnocs.teanekocore.plugin.PluginOwned;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 个人简介数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@PluginOwned("general.info")
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
