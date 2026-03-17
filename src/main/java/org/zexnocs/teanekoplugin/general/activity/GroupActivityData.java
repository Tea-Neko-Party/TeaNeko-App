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
}
