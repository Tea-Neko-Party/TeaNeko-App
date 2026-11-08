package org.zexnocs.teanekoplugin.general.engagement.affection.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.Map;

/**
 * 好感度每日数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@PluginOwned("general.engagement")
public class AffectionDailyData {
    /// 今日增加的总好感度
    @JsonProperty("total_affection")
    private int totalAffection;

    /// 用户 → 今日好感度
    @JsonProperty("daily_affection")
    private Map<String, Integer> dailyAffection;
}
