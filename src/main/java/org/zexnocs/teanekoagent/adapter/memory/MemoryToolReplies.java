package org.zexnocs.teanekoagent.adapter.memory;

import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.memory.profile.UserProfileView;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将检索与画像投影为完整有界 JSON 信封，保留覆盖和来源，不输出管理审计原文。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class MemoryToolReplies {
    static final int EVIDENCE_CHARACTERS = LLMToolSet.DEFAULT_RESULT_CHARACTER_LIMIT - 1024;
    private final ObjectWriter writer;

    /**
     * 使用显式紧凑 writer，工具预算不受全局美化或根包装开关影响。
     * @param mapper 项目 JSON 转换器
     */
    MemoryToolReplies(ObjectMapper mapper) {
        writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE);
    }

    /**
     * 保留证据及原始统计，不重复发送可能任意长的外层 client/scope 标识。
     * @param result 原检索结果
     * @return 包含完整性说明的有界 JSON
     */
    String recall(MemoryRecallResult result) {
        var data = new LinkedHashMap<String, Object>();
        data.put("namespace", result.reference().namespaceId().value());
        data.put("bindingRevision", result.reference().bindingRevision()); data.put("generation", result.reference().generation());
        data.put("observedAt", result.observedAt()); data.put("evidence", result.evidence());
        data.put("scanned", result.scanned()); data.put("matched", result.matched()); data.put("limits", result.limits()); data.put("characters", result.characters());
        data.put("definitivelyEmpty", result.definitivelyEmpty());
        return success(data);
    }

    /**
     * 画像仍只发送一次有序证据，不重复发送 facts/preferences 导致预算膨胀。
     * @param view 原画像视图
     * @return 保留主体、规则版本和覆盖的 JSON
     */
    String profile(UserProfileView view) {
        var data = new LinkedHashMap<String, Object>();
        data.put("namespace", view.reference().namespaceId().value());
        data.put("bindingRevision", view.reference().bindingRevision()); data.put("generation", view.reference().generation());
        data.put("subjectId", view.subjectId()); data.put("projectionVersion", view.projectionVersion()); data.put("observedAt", view.observedAt());
        data.put("evidence", view.evidence()); data.put("scanned", view.scanned()); data.put("matched", view.matched());
        data.put("limits", view.limits()); data.put("characters", view.characters()); data.put("definitivelyEmpty", view.definitivelyEmpty());
        return success(data);
    }

    /**
     * 编码最小成功信封，实际完整长度超限时返回明确错误，不截断 JSON 或伪装空结果。
     * @param data 不含执行资源的业务投影
     * @return 有界完整 JSON
     * @throws RuntimeException 当业务投影无法编码为 JSON 时
     */
    String success(Object data) {
        var encoded = writer.writeValueAsString(Map.of("ok", true, "data", data));
        return encoded.length() <= LLMToolSet.DEFAULT_RESULT_CHARACTER_LIMIT ? encoded : error("RESULT_TOO_LARGE");
    }

    /**
     * 编码仅由适配器固定类别产生的失败信封，不复制异常原文。
     * @param code 内部固定错误码，不能使用模型或异常文本
     * @return 固定结构的失败 JSON
     */
    String error(String code) { return writer.writeValueAsString(Map.of("ok", false, "error", Map.of("code", code))); }
}
