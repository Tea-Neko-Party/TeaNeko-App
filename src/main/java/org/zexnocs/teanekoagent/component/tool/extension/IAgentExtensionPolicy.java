package org.zexnocs.teanekoagent.component.tool.extension;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.concurrent.CompletionStage;

/**
 * 可替换的扩展授权与当前数据复核，真实角色、路径根、执行环境和源版本由宿主判断。
 * 写操作还须核对实际确认及防重，不能从模型参数接受“已授权”布尔值。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IAgentExtensionPolicy {
    /**
     * 在业务执行、资料使用及重放前复核，失败阶段阻止后续动作。
     * @param capability 显式操作
     * @param context 可信来源及借用控制
     * @return 当前授权成立时完成，否则异常结束
     */
    CompletionStage<Void> check(AgentExtensionCapability capability, AgentExtensionContext context);
}
