package org.zexnocs.teanekoagent.component.schedule;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 持久计划的管理投影，不输出动作参数或伪造原生创建者。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
public final class ScheduleManagement {
    /** 禁止实例化契约命名空间。 */
    private ScheduleManagement() {}

    /**
     * 计划查询或取消；创建仍使用原生来源流程。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param action READ、CANCEL 或 OPERATION
     * @param after 原页游标
     * @param id 明确计划标识
     * @param revision 原计划版本
     * @param operationId 原操作编号
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record Query(String action, String after, @Nullable UUID id, long revision, @Nullable UUID operationId) {
        /**
         * @param action 明确动作
         * @param after 原游标
         * @param id 原计划
         * @param revision 原版本
         * @param operationId 原编号
         * @throws IllegalArgumentException 当动作、游标或取消目标与版本无效时
         */
        public Query {
            if (!Set.of("READ", "CANCEL", "OPERATION").contains(action) || after == null || after.length() > 36
                    || !action.equals("READ") && (id == null || operationId == null || revision < 1 || !after.isEmpty())) {
                throw new IllegalArgumentException("计划管理请求无效。");
            }
        }
    }

    /**
     * 计划投影，不混淆实际动作与调度扫描任务。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 计划标识
     * @param title 原标题
     * @param action 原动作键
     * @param state 持久状态
     * @param revision 原版本
     * @param nextFire 下一次发生时间
     * @param zoneId 原展示时区
     * @param intervalSeconds 原固定间隔，零表示单次
     * @param outcome 安全结果
     * @param cancellable 是否允许取消未来计划
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record View(UUID id, String title, String action, String state, long revision, Instant nextFire,
                       String zoneId, long intervalSeconds, String outcome, boolean cancellable) {}

    /**
     * 原始扫描页的过滤投影，空页仍可能有后页。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param items 当前聊天可见计划
     * @param next 原扫描游标
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record Page(List<View> items, String next) {}

    /**
     * 与计划同事务提交的取消事实。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param operationId 原编号
     * @param status APPLIED 或 UNCHANGED
     * @param target 原计划标识
     * @param generation 固定为负一，不冒充记忆代次
     * @param revision 提交时计划版本
     * @param note 原事实说明
     * @param at 原提交时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    public record Receipt(UUID operationId, String status, String target, long generation, long revision, String note, Instant at) {}

    /**
     * 私有幂等绑定，无动作参数和用户消息。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param actor 可信主体摘要
     * @param scope 真实聊天标识
     * @param request 原取消请求
     * @param receipt 原提交回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    record Operation(String actor, String scope, Query request, Receipt receipt) {}

    /**
     * 有界持久回执窗口。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param operations 最多二百五十六项，保留一天
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    record Journal(Map<UUID, Operation> operations) {}
}
