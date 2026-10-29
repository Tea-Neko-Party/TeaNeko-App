package org.zexnocs.teanekoagent.llm.framework.message;

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一完整历史与工具调用校验，供交互循环和上层窗口提交复用，不执行工具。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMMessageHistory {
    /** 禁止实例化无状态协议校验器。 */
    private LLMMessageHistory() {}

    /**
     * 校验已冻结历史的工具调用、结果名称和顺序，拒绝不完整或重复的调用链。
     * @param messages 已冻结消息
     * @return 历史中已经使用的调用 ID，调用方可用于校验后继响应
     * @throws IllegalArgumentException 当工具历史不完整、孤立或重复时
     */
    public static Set<String> validateComplete(List<ILLMMessage> messages) {
        var ids = new HashSet<String>();
        var pending = new ArrayDeque<ILLMToolCall>();
        for (var message : messages) {
            if (message instanceof ILLMToolMessage tool) {
                var expected = pending.pollFirst();
                if (expected == null || !expected.getId().equals(tool.getToolCallId()) || !expected.getName().equals(tool.getName())) {
                    throw new IllegalArgumentException("工具历史的调用 ID、名称或顺序不匹配。");
                }
            } else {
                if (!pending.isEmpty()) { throw new IllegalArgumentException("工具调用尚未完整回填，不能继续其他消息。"); }
                if (message instanceof ILLMAssistantMessage assistant) {
                    validateCalls(assistant.getToolCalls(), ids);
                    pending.addAll(assistant.getToolCalls());
                }
            }
        }
        if (!pending.isEmpty()) { throw new IllegalArgumentException("初始历史包含未完成的工具调用。"); }
        return ids;
    }

    /**
     * 先校验整批格式及全链 ID，成功后才向调用方集合合并本批标识。
     * @param calls 已冻结调用列表
     * @param ids 当前历史已用 ID，成功后加入本批 ID
     * @throws IllegalArgumentException 当工具协议非法或调用 ID 重复时
     */
    public static void validateCalls(List<ILLMToolCall> calls, Set<String> ids) {
        var added = new HashSet<String>();
        for (var call : calls) {
            if (!"function".equals(call.getType()) || call.getId() == null || call.getId().isBlank()
                    || call.getName() == null || call.getName().isBlank() || call.getArguments() == null
                    || ids.contains(call.getId()) || !added.add(call.getId())) {
                throw new IllegalArgumentException("模型工具调用格式非法或 ID 已使用。");
            }
        }
        ids.addAll(added);
    }
}
