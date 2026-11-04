package org.zexnocs.teanekoagent.integration.web;

import org.springframework.http.ResponseEntity;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoapp.web.error.IWebFailureMapper;
import org.zexnocs.teanekoapp.web.error.WebFailureMapping;

import java.util.Map;

/**
 * Agent 主动向 App 提供资料失败的安全 HTTP 映射，不让 App 反向依赖 Agent 类型。
 * 保留原状态码、稳定错误码及中文说明，不读取或回显失败资料。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@WebFailureMapping(type = AgentPromptException.class)
public final class AgentWebFailureMapper implements IWebFailureMapper {
    /**
     * @param failure 已由声明类型匹配的 Agent 资料异常
     * @return 原管理界面可识别的固定业务失败
     */
    @Override
    public ResponseEntity<Map<String, String>> map(Throwable failure) {
        var prompt = (AgentPromptException) failure;
        return switch (prompt.reason()) {
            case DISABLED -> error(409, "AGENT_FEATURE_DISABLED",
                    "此聊天尚未登记或启用 Agent 相关功能，请先在聊天配置中登记并启用。");
            case CHANGED -> error(409, "AGENT_REFERENCE_CHANGED", "资料绑定或代次已变化，请重新读取。");
            case UNAVAILABLE -> error(503, "AGENT_REFERENCE_UNAVAILABLE", "资料暂不可用，请检查人格或记忆配置。");
            case INPUT_TOO_LARGE -> error(400, "AGENT_INPUT_TOO_LARGE", "本次输入超过资料容量限制，请缩短内容。");
        };
    }

    /**
     * @param status 固定错误状态
     * @param code 稳定错误码
     * @param message 不含异常原文的安全说明
     * @return 不附带资料、连接或原异常的响应
     */
    private static ResponseEntity<Map<String, String>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
