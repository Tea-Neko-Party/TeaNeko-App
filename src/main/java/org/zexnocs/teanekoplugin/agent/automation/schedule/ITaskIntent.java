package org.zexnocs.teanekoplugin.agent.automation.schedule;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.Instant;
import java.util.UUID;

/**
 * 自然语言解析产生的纯任务候选，不包含身份、权限或平台发送能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public sealed interface ITaskIntent {
    /**
     * 已消除时间歧义的创建候选，仍须通过当前原生权限和数据库防重。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param text 字面提醒正文
     * @param firstFire 首次时刻
     * @param zone 明确展示时区
     * @param intervalSeconds 固定重复秒数
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Create(String text, Instant firstFire, String zone, long intervalSeconds) implements ITaskIntent { }

    /**
     * 查看或取消指定任务，不提供其他用户或 scope 参数。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 任务标识
     * @param cancel 是否取消
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Inspect(UUID id, boolean cancel) implements ITaskIntent { }

    /**
     * 查询当前来源的任务列表。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param after 上页稳定游标，首页为空
     * @param onlyMine 是否限定本人创建的任务
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record ListTasks(String after, boolean onlyMine) implements ITaskIntent { }

    /**
     * 时间或操作不明确时仅返回澄清提示，不执行动作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param reason 固定中文提示
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Clarify(String reason) implements ITaskIntent { }
}
