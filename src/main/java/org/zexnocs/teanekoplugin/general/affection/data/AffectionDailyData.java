package org.zexnocs.teanekoplugin.general.affection.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 好感度每日数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AffectionDailyData {
    /// 今日增加的总好感度
    @JsonProperty("total_affection")
    private int totalAffection;

    /// 用户 → 今日好感度
    @JsonProperty("daily_affection")
    private Map<String, Integer> dailyAffection;
}
