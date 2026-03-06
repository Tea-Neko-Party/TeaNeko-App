package org.zexnocs.teanekoplugin.general.affection.interfaces;

import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.List;
import java.util.UUID;

/**
 * 用于记录和管理群组成员之间好感度的服务接口
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
public interface IAffectionService {
    /**
     * 在每日限制内的好感度增加
     *
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @param amount 增加的好感度数值
     * @return 是否成功增加好感度
     */
    boolean increaseAffectionDailyLimit(UUID senderId, UUID targetId, int amount);

    /**
     * 直接增加好感度
     *
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @param amount 增加的好感度数值
     */
    void increaseAffection(UUID senderId, UUID targetId, int amount);

    /**
     * 获取当前好感度
     *
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @return 当前好感度数值
     */
    int getAffection(UUID senderId, UUID targetId);

    /**
     * 获得作为发送者用户的好感度前 k 名的目标用户 ID 和 对应好感度数值
     *
     * @param senderId 发送者用户ID
     * @param k 前 k 名
     * @return 目标用户 ID 和 对应好感度数值的列表
     */
    List<Pair<UUID, Integer>> getTopKAffectionTargets(UUID senderId, int k);

    /**
     * 获取作为目标者用户的好感度前 k 名的发送者用户ID 列表
     *
     * @param targetId 目标用户ID
     * @param k 前 k 名
     * @return 发送者用户ID 列表
     */
    List<Pair<UUID, Integer>> getTopKAffectionSenders(UUID targetId, int k);
}
