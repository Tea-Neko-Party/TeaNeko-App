package org.zexnocs.teanekoplugin.general.affection;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekoplugin.general.affection.data.AffectionDailyData;
import org.zexnocs.teanekoplugin.general.affection.interfaces.IAffectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于记录和管理群组成员之间好感度的服务
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service
public class AffectionService implements IAffectionService {
    /// 初始好感度，用于防止负数好感度出现
    private static final int DEFAULT_AFFECTION = 100000;
    private static final String NAMESPACE = "affection_system";

    /// 每日好感度增加上限
    private static final int MAX_DAILY_AFFECTION_INCREASE = 50;

    /// 每日总好感增加上限
    private static final int MAX_DAILY_TOTAL_AFFECTION_INCREASE = 500;
    private final IItemDataService iItemDataService;

    public AffectionService(IItemDataService iItemDataService) {
        this.iItemDataService = iItemDataService;
    }

    /**
     * 在每日限制内的好感度增加
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @param amount 增加的好感度数值
     * @return 是否成功增加好感度
     */
    @Override
    public boolean increaseAffectionDailyLimit(UUID senderId, UUID targetId, int amount) {
        // 参数检查
        if(amount < 0 || amount > MAX_DAILY_AFFECTION_INCREASE) {
            return false;
        }
        if(amount == 0) {
            return true;
        }
        // 先判断是否达到每日上限
        var DailyKey = ChinaDateUtil.Instance.getNowDateString();
        var senderData = CleanableEasyData.of(NAMESPACE).get(senderId.toString());
        var todayData = senderData.get(DailyKey, AffectionDailyData.class);
        int totalAffection = 0;
        int singleAffection = 0;
        if (todayData != null) {
            totalAffection = todayData.getTotalAffection();
            singleAffection = todayData.getDailyAffection().getOrDefault(targetId.toString(), 0);

            // 超过了每日总上限
            if(totalAffection + amount > MAX_DAILY_TOTAL_AFFECTION_INCREASE) {
                return false;
            }
            // 超过了对单个目标的每日上限
            if(singleAffection + amount > MAX_DAILY_AFFECTION_INCREASE) {
                return false;
            }
        }
        int finalSingleAffection = singleAffection;
        int finalTotalAffection = totalAffection;
        // 获取或创建好感度数据
        var dto = iItemDataService.get(NAMESPACE, senderId, targetId.toString());
        var config = dto.getDatabaseTaskConfig("增加好感度");
        var config2 = senderData.getTaskConfig("更新每日好感度数据");
        // 更新好感度
        if(dto.getCount() == 0) {
            // 如果为 0 则说明之前没有好感度数据，设置为默认值进行初始化
            config.setCount(DEFAULT_AFFECTION);
        }
        config.addCount(amount);
        // 更新每日好感度数据
        Map<String, Integer> singleAffectionMap = todayData != null ?
                todayData.getDailyAffection() :
                new ConcurrentHashMap<>();
        singleAffectionMap.put(targetId.toString(), finalSingleAffection + amount);
        var newTodayData = new AffectionDailyData(
                finalTotalAffection + amount,
                singleAffectionMap);
        config2.set(DailyKey, newTodayData);
        // 合并并推送
        config.merge(config2)
                .push();
        return true;
    }

    /**
     * 直接增加好感度
     *
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @param amount 增加的好感度数值
     */
    @Override
    public void increaseAffection(UUID senderId, UUID targetId, int amount) {
        // 参数检查
        if(amount <= 0) {
            // 不处理非正数的增加请求
            return;
        }
        // 获取或创建好感度数据
        var dto = iItemDataService.get(NAMESPACE, senderId, targetId.toString());
        var config = dto.getDatabaseTaskConfig("增加好感度");
        // 更新好感度
        if(dto.getCount() == 0) {
            // 如果为 0 则说明之前没有好感度数据，设置为默认值进行初始化
            config.setCount(DEFAULT_AFFECTION);
        }
        config.addCount(amount);
        // 推送
        config.push();
    }

    /**
     * 获取当前好感度
     *
     * @param senderId 发送者用户ID
     * @param targetId 目标用户ID
     * @return 当前好感度数值
     */
    @Override
    public int getAffection(UUID senderId, UUID targetId) {
        // 获取好感度数据
        var item = iItemDataService.get(NAMESPACE, senderId, targetId.toString());
        if(item.getCount() == 0) {
            // 没有数据，说明好感度为默认值
            return 0;
        }
        return item.getCount() - DEFAULT_AFFECTION;
    }

    /**
     * 获得作为发送者用户的好感度前 k 名的目标用户ID 列表
     *
     * @param senderId 发送者用户ID
     * @param k 前 k 名
     * @return 目标用户ID 列表
     */
    @Override
    public List<Pair<UUID, Integer>> getTopKAffectionTargets(UUID senderId, int k) {
        // 获取好感度数据列表
        var type2item = iItemDataService.getByOwner(NAMESPACE, senderId);
        // 排序并获取前 k 名
        List<Pair<UUID, Integer>> list = new ArrayList<>();
        for(var entry: type2item.entrySet()) {
            var key = entry.getKey();
            var item = entry.getValue();
            int affection = item.getCount() - DEFAULT_AFFECTION;
            list.add(IndependentPair.of(UUID.fromString(key), affection));
        }
        // 根据 affection 排序
        list.sort((a, b) -> Integer.compare(b.second(), a.second()));
        // 返回前 k 名
        if(list.size() > k) {
            return list.subList(0, k);
        } else {
            return list;
        }
    }
    /**
     * 获取作为目标者用户的好感度前 k 名的发送者用户ID 列表
     *
     * @param targetId 目标用户ID
     * @param k 前 k 名
     * @return 发送者用户ID 列表
     */
    @Override
    public List<Pair<UUID, Integer>> getTopKAffectionSenders(UUID targetId, int k) {
        // 获取好感度数据列表
        var senderId2item = iItemDataService.getByType(
                NAMESPACE,
                String.valueOf(targetId));
        // 排序并获取前 k 名
        List<Pair<UUID, Integer>> list = new ArrayList<>();
        for (var entry : senderId2item.entrySet()) {
            var key = entry.getKey();
            var item = entry.getValue();
            int affection = item.getCount() - DEFAULT_AFFECTION;
            list.add(IndependentPair.of(key, affection));
        }
        // 根据 affection 排序
        list.sort((a, b) -> Integer.compare(b.second(), a.second()));
        // 返回前 k 名
        if (list.size() > k) {
            return list.subList(0, k);
        } else {
            return list;
        }
    }
}
