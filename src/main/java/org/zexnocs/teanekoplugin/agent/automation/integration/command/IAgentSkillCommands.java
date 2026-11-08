package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * AgentSkillCommands 的类型化业务端口；实现由所属插件提供，底层不依赖插件实现。
 * <br>2026/11/07：分离声明与实现，保留原真实完成和权限约束。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentSkillCommands {
    /**
     * 读取当前允许列表或由真实 DEBUG 重载；重载在发布前复核授权，失败保留旧目录。
     * @param data 原生来源
     * @param reload 是否重载
     * @param parent 借用父控制
     * @return 等待操作和资源观察结束的反馈
     */
    CompletionStage<String> execute(ITeaNekoMessageData data, boolean reload, @Nullable ExecutionControl parent);
}
