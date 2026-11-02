package org.zexnocs.teanekoagent.schedule;

import java.time.Instant;
import java.util.UUID;

/**
 * 自然语言解析产生的纯任务候选，不包含身份、权限或平台发送能力。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
public sealed interface TaskIntent {
    /**
     * 已消除时间歧义的创建候选，仍须通过当前原生权限和数据库防重。
     * @param text 字面提醒正文
     * @param firstFire 首次时刻
     * @param zone 明确展示时区
     * @param intervalSeconds 固定重复秒数
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Create(String text, Instant firstFire, String zone, long intervalSeconds) implements TaskIntent { }

    /**
     * 查看或取消指定任务，不提供其他用户或 scope 参数。
     * @param id 任务标识
     * @param cancel 是否取消
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Inspect(UUID id, boolean cancel) implements TaskIntent { }

    /**
     * 查询当前来源的任务列表。
     * @param after 上页稳定游标，首页为空
     * @param onlyMine 是否限定本人创建的任务
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record ListTasks(String after, boolean onlyMine) implements TaskIntent { }

    /**
     * 时间或操作不明确时仅返回澄清提示，不执行动作。
     * @param reason 固定中文提示
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Clarify(String reason) implements TaskIntent { }
}
