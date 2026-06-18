package org.zexnocs.teanekoagent_old.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话上下文注册服务。
 * <br>内置 Agent 客户端通过该服务在多次 sender 调用之间复用同一会话。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentConversationRegistryService {
    /** Agent 上下文创建服务。 */
    private final AgentContextService contextService;

    /** 会话 ID 到上下文的映射。 */
    private final Map<String, AgentConversationContext> contexts = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话上下文。
     *
     * @param conversationId 会话 ID
     * @param scopeId        作用域 ID
     * @param agentId        Agent ID
     * @param userId         用户 ID
     * @return 可复用的会话上下文
     */
    public AgentConversationContext getOrCreate(String conversationId,
                                                String scopeId,
                                                String agentId,
                                                String userId) {
        var key = requireText(conversationId, "conversationId");
        return contexts.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return contextService.createContext(key, scopeId, agentId, userId);
            }
            requireCompatible(existing, scopeId, userId);
            return existing;
        });
    }

    /**
     * 查找已存在的会话上下文。
     *
     * @param conversationId 会话 ID
     * @return 会话上下文
     */
    public Optional<AgentConversationContext> find(String conversationId) {
        return Optional.ofNullable(contexts.get(safe(conversationId)));
    }

    /** 校验复用会话的身份边界。 */
    private static void requireCompatible(AgentConversationContext context, String scopeId, String userId) {
        if (!context.getScopeId().equals(safe(scopeId)) || !context.getUserId().equals(safe(userId))) {
            throw new IllegalArgumentException("Conversation ID is already bound to another scope or user");
        }
    }

    /** 获取必填文本。 */
    private static String requireText(String value, String name) {
        var checked = safe(value);
        if (checked.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked;
    }

    /** 规范化可空文本。 */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
