package org.zexnocs.teanekoplugin.general.signin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 签到记录数据，记录在 {@link org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData} 中。
 *
 * @author zExNocs
 * @date 2026/03/05
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SignInData {
    /**
     * 本次签到的时间。
     */
    @JsonProperty("sign_in_time")
    private String signInTime;

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
