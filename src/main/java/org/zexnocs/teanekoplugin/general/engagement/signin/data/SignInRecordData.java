package org.zexnocs.teanekoplugin.general.engagement.signin.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 签到记录数据，记录在 {@link org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData} 中。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @version 4.9.0-l
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@PluginOwned("general.engagement")
public class SignInRecordData {
    /**
     * 本次签到的时间。
     */
    @JsonProperty("time")
    private String time;

    /**
     * 本次签到获取的金币数量。
     */
    @JsonProperty("coin")
    private int coin;

    /**
     * 本次签到的幸运数字。
     */
    @JsonProperty("lucky_number")
    private int luckyNumber;
}
