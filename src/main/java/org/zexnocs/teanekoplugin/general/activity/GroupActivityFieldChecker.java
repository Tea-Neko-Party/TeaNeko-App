package org.zexnocs.teanekoplugin.general.activity;

import org.springframework.scheduling.support.CronExpression;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.time.Duration;

/**
 * 用于检测设置 {@link GroupActivityData} 部分域是否合理。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
public class GroupActivityFieldChecker implements IConfigFieldChecker {
    /**
     * 检测当前域的设置值是否合理。
     * <br>如果合理则返回
     * {@code null}
     * <br>如果不合理则返回报错信息。
     *
     * @param field 域名
     * @param value 值
     * @return {@link String }
     */
    @Override
    public String isValid(String field, String value) {
        // rules
        return switch (field) {
            case "rules" -> checkRules(value);
            case "cron" -> checkCron(value);
            case "kick" -> checkKick(value);
            case "remind" -> checkRemind(value);
            default -> null;
        };

    }

    /**
     * 检测 kick >= 1
     */
    private String checkKick(String value) {
        try {
            var intValue = Integer.parseInt(value);
            if(intValue < 1) {
                return "kick 的值必须大于等于 1。";
            }
            return null;
        } catch (NumberFormatException e) {
            return "kick 的值应为整数。";
        }
    }

    /**
     * 检测 remind >= 1
     */
    private String checkRemind(String value) {
        try {
            var intValue = Integer.parseInt(value);
            if(intValue < 1) {
                return "remind 的值必须大于等于 1。";
            }
            return null;
        } catch (NumberFormatException e) {
            return "remind 的值应为整数。";
        }
    }

    /**
     * 检测 rules 是否合理。
     *
     */
    private String checkRules(String value) {
        // 构造一个假 GroupActivityData 对象用于解析
        var fakeData = GroupActivityData.getFakeData();
        try {
            // 检测是否能通过假数据
            new GroupActivityRule(value).isValid(fakeData);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    /**
     * 检测 cron
     *
     */
    private String checkCron(String value) {
        try {
            var cron = CronExpression.parse(value);
            // 检测表达式最小间隔是否超过了两小时
            var base = ChinaDateUtil.Instance.getNowZonedDateTime();
            long min = Long.MAX_VALUE;
            for (int i = 0; i < 5; i++) {
                var t1 = cron.next(base);
                if(t1 == null) {
                    // 如果为 null，说明只触发一次，直接返回 null
                    return null;
                }
                var t2 = cron.next(t1);
                long diff = Duration.between(t1, t2).toSeconds();
                min = Math.min(min, diff);
                base = t1;
            }

            // 检测是否大于两小时
            if(min < Duration.ofHours(2).toSeconds()) {
                return "Cron 表达式的触发间隔必须至少为两小时。";
            }
        } catch (IllegalArgumentException e) {
            return "Cron 表达式不合法，解析失败。";
        }
        return null;
    }
}
