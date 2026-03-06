package org.zexnocs.teanekoplugin.general.info.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @JsonProperty("time")
    private long time;
}
