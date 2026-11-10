package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 原生生成响应，保留候选结构供协议映射验证，缺失用量保持未知。
 * <br>2026/11/10：隔离原生候选、阻止原因与用量，不把思考正文作为用户回答。
 *
 * @param responseId 上游响应编号
 * @param modelVersion 上游实际型号
 * @param candidates 原始候选，允许拒答时缺失
 * @param promptFeedback 输入阻止信息
 * @param usageMetadata 供应商用量
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public record AntigravityResponseData(@Nullable String responseId, @Nullable String modelVersion,
        @Nullable List<JsonNode> candidates, @Nullable JsonNode promptFeedback, @Nullable JsonNode usageMetadata)
        implements IAPIResponseData {
}
