package org.zexnocs.teanekoagent.expression;

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.List;
import java.util.function.Function;

/**
 * 完整回复的语法解析器，不发送消息或改变真实目标。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
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
