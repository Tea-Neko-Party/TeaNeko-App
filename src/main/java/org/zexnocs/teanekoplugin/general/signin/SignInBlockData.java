package org.zexnocs.teanekoplugin.general.signin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一个签到区块的数据类。
 * 签到区块是用来
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.1.0
 */
@Getter
@AllArgsConstructor
public class SignInBlockData {
    /**
     * 该签到块的最后一次签到日期。
     * 使用 yyyy-MM-dd 格式的字符串表示，例如 "2026-03-05"。
     */
    @JsonProperty("last_date")
    private String lastDate;

    /**
     * 该签到块的连续签到天数。
     * 第一天时间 = last_date - continuous + 1
     */
    @JsonProperty("continuous")
    private int continuous;
}
