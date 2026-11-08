package org.zexnocs.teanekoplugin.agent.automation.extension;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 可替换的受控代码执行端口；实现负责隔离环境和真实授权，默认不安装宿主命令执行器。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public interface IAgentCodeExecutor {
    /** @return 本地显式支持的语言名称，不读取模型或启动执行环境 */
    Set<String> languages();
    /**
     * 在声明的隔离环境执行候选，不将取消观察解释为回滚外部副作用。
     * @param language 已在本地声明的语言
     * @param code 有界候选正文
     * @param context 来源、防重及借用控制
     * @return 实际结束回执，输出由后端脱敏并限长
     */
    CompletionStage<Result> execute(String language, String code, AgentExtensionContext context);
    /**
     * 代码执行的显式业务结果，不用任意 stdout 文本声明成功。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param status 已完成、拒绝或不确定
     * @param output 脱敏且有界的输出
     * @param complete 输出是否完整
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Result(AgentExtensionStatus status, String output, boolean complete) {}
}
