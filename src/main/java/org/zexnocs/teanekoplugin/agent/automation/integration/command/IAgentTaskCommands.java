package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * AgentTaskCommands 的类型化业务端口；实现由所属插件提供，底层不依赖插件实现。
 * <br>2026/11/07：分离声明与实现，保留原真实完成和权限约束。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentTaskCommands {
    /**
     * 只消费宿主提取的当前原文，识别后不调用模型；派生命令不提供此捷径。
     * @param data 原生消息及固定发送时刻
     * @param original 当前真实正文
     * @return 已识别意图的实际处理阶段；普通对话或旧宿主为空
     */
    Optional<CompletionStage<String>> natural(ITeaNekoMessageData data, String original);
    /**
     * 明确偏移与 ZoneId 必须一致，夏令时空洞拒绝，重复请求使用相同绝对时间及规格。
     * @param data 真实消息
     * @param time ISO 偏移时间
     * @param zone 明确时区
     * @param intervalSeconds 固定重复秒数，零为单次
     * @param words 提醒正文
     * @param parent 借用父控制
     * @return 持久化后的任务卡片
     */
    CompletionStage<String> create(ITeaNekoMessageData data, String time, String zone, long intervalSeconds,
            List<String> words, @Nullable ExecutionControl parent);
    /**
     * 查询当前来源中本人或当前群管理员可见的任务，游标不接受任意无界文本。
     * @param data 原生消息
     * @param after 上页游标，空字符串表示首页
     * @param parent 借用父控制
     * @return 文本列表
     */
    CompletionStage<String> list(ITeaNekoMessageData data, String after, @Nullable ExecutionControl parent);
    /**
     * 查看或取消固定任务，不从参数接受创建者或 scope。
     * @param data 原生消息
     * @param id 任务 UUID
     * @param cancel 是否取消
     * @param parent 借用父控制
     * @return 当前任务卡片
     */
    CompletionStage<String> inspect(ITeaNekoMessageData data, String id, boolean cancel, @Nullable ExecutionControl parent);
}
