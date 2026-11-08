package org.zexnocs.teanekoplugin.agent.social.social;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 同一记忆空间中面向真实用户的关系及短期情绪；不代表对人的客观评价或权限。
 * <br>2026/11/06：持久记录关怀次数与未决发送，避免重启后继续反复追问。
 * @param affinity 亲近度零至一百
 * @param mood 心情负一百至一百
 * @param moodAt 情绪更新时间
 * @param appraisedAt 上次有效评估
 * @param evidenceHash 最近证据摘要
 * @param lastUser 最近真实来信时间
 * @param sourceKey 最近消息身份
 * @param lastCare 最近关怀确认
 * @param reminders 未回复关怀次数
 * @param pending 未决关怀身份
 * @param nextCare 下次允许候选时间
 * @param revision 状态版本
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
public record SocialState(int affinity, int mood, Instant moodAt, Instant appraisedAt, String evidenceHash,
                          Instant lastUser, String sourceKey, Instant lastCare, int reminders,
                          String pending, Instant nextCare, long revision) {
    /**
     * 校验持久数值，不把损坏状态当作新关系。
     * @param affinity 亲近度
     * @param mood 心情
     * @param moodAt 心情时刻
     * @param appraisedAt 评估时刻
     * @param evidenceHash 证据摘要
     * @param lastUser 来信时刻
     * @param sourceKey 消息身份
     * @param lastCare 关怀确认
     * @param reminders 未回复次数
     * @param pending 未决身份
     * @param nextCare 下次候选时刻
     * @param revision 版本
     */
    public SocialState {
        // 2026/11/06：持久对象的必填字段损坏时立即报告，避免延迟到候选或提示构建才失败。
        Objects.requireNonNull(moodAt, "心情更新时间不能为空。");
        Objects.requireNonNull(appraisedAt, "关系评估时间不能为空。");
        Objects.requireNonNull(evidenceHash, "关系证据摘要不能为空。");
        Objects.requireNonNull(lastUser, "最近来信时间不能为空。");
        Objects.requireNonNull(sourceKey, "最近消息身份不能为空。");
        Objects.requireNonNull(lastCare, "最近关怀时间不能为空。");
        Objects.requireNonNull(pending, "关怀占用字段不能为空。");
        Objects.requireNonNull(nextCare, "下次关怀时间不能为空。");
        if (affinity < 0 || affinity > 100 || mood < -100 || mood > 100 || reminders < 0 || revision < 0) {
            throw new IllegalArgumentException("关系或心情持久状态范围无效。");
        }
    }

    /**
     * @param initial 初始亲近度
     * @return 尚未观察过来信的状态
     */
    public static SocialState initial(int initial) {
        return new SocialState(initial, 0, Instant.EPOCH, Instant.EPOCH, "", Instant.EPOCH, "", Instant.EPOCH,
                0, "", Instant.EPOCH, 0);
    }

    /**
     * @param now 当前时间
     * @param hours 回归平静所需小时
     * @return 自然衰减后的心情，不改变关系
     */
    public int currentMood(Instant now, int hours) {
        double remaining = Math.max(0, 1 - Math.max(0, Duration.between(moodAt, now).toSeconds()) / (hours * 3600.0));
        return (int) Math.round(mood * remaining);
    }
}
