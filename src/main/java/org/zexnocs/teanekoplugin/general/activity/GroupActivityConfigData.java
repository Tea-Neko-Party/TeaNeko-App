package org.zexnocs.teanekoplugin.general.activity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 群活跃度管理器的配置类。
 *
 * @see GroupActivityData
 * @author zExNocs
 * @date 2026/03/17
 * @since 4.3.4
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupActivityConfigData implements IConfigData {
    @Description("发送活跃度消息的群ID列表，即监控该群的群。")
    private List<String> groups = new CopyOnWriteArrayList<>();

    @Description("""
            活跃度 SpEL规则表达式列表。
            要求最终转化成布尔值，如果返回 "true" 则表示该用户活跃度不足。
            SpEL 表达式规则如下 (详细可自行搜索)：
            1. 计算符：
                a. 通用：括号
                b. 布尔运算：&&; ||; !取反;
                c. 数字运算：+; -; *; /; ^次方; <; <=; >; >=; ==;
                d. 字符串：matches '正则表达式'
            2. 支持字段：
                a. nickname 字符串-成员昵称
                b. card     字符串-成员群昵称
                c. join     数字-至今为止加入群多少天
                d. speak    数字-至今为止多少天没有说话
                e. level    数字-群等级
                f. hasTitle 布尔-是否有头衔""")
    private List<String> rules = new CopyOnWriteArrayList<>();

    @Description("""
            活跃度自动检测日期，符合 Cron 表达式。
            例如 "0 50 17 * * *" 表示每日 17:50:00 触发。
            要求间隔时间至少为 2 小时。
            如果为 null 则表示不触发。
            详细请自行搜索 Cron 表达式。
            请使用 /ga update-cron 更新计时器""")
    private String cron = null;

    @Description("""
            踢出低活跃度至少需要多少成员确认
            至少为 1""")
    private int kick = 2;

    @Description("""
            警告低活跃度至少需要多少成员确认
            至少为 1""")
    private int remind = 2;
}
