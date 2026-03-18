package org.zexnocs.teanekoplugin.general.activity;

import lombok.Builder;
import lombok.Getter;

/**
 * 支持解析成表达式的活跃度数据
 *
 * @author zExNocs
 * @date 2026/03/17
 * @since 4.3.4
 */
@Getter
@Builder
public class GroupActivityData {
    /// 昵称
    private final String nickname;

    /// 成员群昵称
    private final String card;

    /// 至今为止加入群多少天
    private final int join;

    /// 至今为止多少天没有说话
    private final int speak;

    /// 群等级
    private final int level;

    /// 是否有头衔
    private final boolean hasTitle;

    /// 其 title
    private final String title;

    /// 实际加入时间的时间戳 (ms)
    private final long joinTimeMs;

    /// 实际上次说话的时间戳 (ms)
    private final long lastSpeakTimeMs;

    /**
     * 获取一个 fake data
     *
     * @return fake data
     */
    public static GroupActivityData getFakeData() {
        var currentTime = System.currentTimeMillis();
        return builder()
                .nickname("user")
                .card("group_member")
                .join(30)
                .speak(10)
                .level(20)
                .hasTitle(true)
                .joinTimeMs(currentTime)
                .lastSpeakTimeMs(currentTime)
                .title("title")
                .build();
    }
}
