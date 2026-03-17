package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;

/**
 * 群活跃度豁免服务。
 * <br>用于记录豁免成员，以及自动删除过期的豁免成员。
 *
 * @author zExNocs
 * @date 2026/03/17
 * @since 4.3.4
 */
@Service
@RequiredArgsConstructor
public class GroupActivityExemptionService {
    /// 豁免人员的数据库命名空间
    public static final String DATABASE_NAMESPACE = "activity";

    /// 豁免人员的数据库列表名称
    public static final String DATABASE_LIST_TARGET = "exemption";

    /**
     * 添加豁免成员。
     *
     * @see TaskFuture
     * @param scopeId      群组的区域 ID
     * @param userId       用户在平台中的 ID
     * @param durationInMs 豁免的持续时间。毫秒。
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link Void }>> 数据库完成 future，请自行 finish()
     */
    public TaskFuture<ITaskResult<Void>> addWithFuture(String scopeId, String userId, long durationInMs) {
        long exemptionTime = System.currentTimeMillis() + durationInMs;
        // 添加到数据库中
        return getDto().getTaskConfig("添加豁免成员")
                .set(getKey(scopeId, userId), exemptionTime)
                .pushWithFuture();
    }

    /**
     * 添加豁免成员。
     *
     * @param scopeId      群组的区域 ID
     * @param userId       用户在平台中的 ID
     * @param durationInMs 豁免的持续时间。毫秒。
     */
    public void add(String scopeId, String userId, long durationInMs) {
        addWithFuture(scopeId, userId, durationInMs).finish();
    }

    /**
     * 删除豁免成员。
     *
     * @param scopeId      群组的区域 ID
     * @param userId       用户在平台中的 ID
     */
    public void remove(String scopeId, String userId) {
        getDto().getTaskConfig("删除过期豁免成员")
                .remove(getKey(scopeId, userId))
                .push();
    }

    /**
     * 判断成员是否在豁免名单中；
     * <br>如果在豁免名单中，判断是否已经过期，如果过期会删除该豁免名单并返回 false
     *
     * @return 是否在豁免名单中
     */
    public boolean isExempted(String scopeId, String userId, long currentTimeInMs) {
        // 尝试查找，如果没找到则返回 false
        Long exemptionTime = getDto().get(getKey(scopeId, userId), Long.class);
        if (exemptionTime == null) {
            return false;
        }
        // 判断是否已经过期
        if (exemptionTime < currentTimeInMs) {
            // 已过期，删除该豁免名单
            remove(scopeId, userId);
            return false;
        }
        // 否则返回 true
        return true;
    }

    /**
     * 获取 dto
     *
     * @return 豁免成员 dto
     */
    public IEasyDataDto getDto() {
        return GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
    }

    /**
     * 根据 groupId, userId 构造 key
     *
     * @param scopeId  群组的区域 ID
     * @param userId   用户在平台中的 ID
     * @return 构造的 key
     */
    public String getKey(String scopeId, String userId) {
        return "(%s, %s)".formatted(scopeId, userId);
    }
}
