package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.List;
import java.util.function.Function;

/**
 * 完整回复的语法解析器，不发送消息或改变真实目标。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
public interface IReplyProtocolParser {
    /**
     * 向模型提供当前协议语法；兼容协议可以不额外提示。
     * @param nonce 当轮标识
     * @return 有界语法说明
     */
    default String instructions(String nonce) { return ""; }
    /**
     * 通过当前协议的指令目录解析完整答案。
     * @param answer 正式答案
     * @param nonce 原当轮标识
     * @param directives 指令解析入口
     * @return 不可变草稿
     */
    List<ReplyPlans.Draft> parse(ILLMAssistantMessage answer, String nonce, Function<ReplyPlans.Directive,ReplyPlans.Draft> directives);
}
