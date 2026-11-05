package org.zexnocs.teanekoagent.component.llm.framework.structured;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 共享严格解码器，语法、字段结构与领域构造分别校验，禁止裁剪后伪造合法候选。
 * <br>2026/11/06：保留安全行列、字段及原输出结构，正文值不进入普通日志。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class LLMJsonCodec {
    private final ObjectMapper mapper;

    /** @param mapper 原应用转换器，保留既有类型模块但收紧外部输入 */
    public LLMJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    }

    /**
     * 只绑定完整、符合当前合同的对象，缺字段、额外字段和隐式类型转换均拒绝。
     * @param type 实际目标类型
     * @param definition 初始化所得合同
     * @param text 最终可见输出，不含模型推理
     * @param request 原请求编号；无上下文的兼容入口可以为空
     * @param <T> 返回类型
     * @return 完整领域对象
     * @throws LLMJsonException 当容量、语法、字段或领域约束不符合合同时
     */
    public <T> T read(Class<T> type, LLMJsonSchemas.Definition definition, String text, @Nullable UUID request) {
        var schema = mapper.valueToTree(definition.schema().schema());
        if (text.length() > definition.maxCharacters()) {
            throw failure(LLMJsonException.Code.OUTPUT_LIMIT, request, definition, text, schema,
                    "字符数=" + text.length() + "，上限=" + definition.maxCharacters(), null);
        }
        final JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (JacksonException invalid) {
            var location = invalid.getLocation();
            String at = location == null ? "行列未知" : "行=" + location.getLineNr() + "，列=" + location.getColumnNr();
            throw failure(LLMJsonException.Code.JSON_SYNTAX, request, definition, text, schema,
                    "不是完整 JSON；" + at, invalid);
        }
        var mismatch = validate(root, schema, "$", 0);
        if (!mismatch.isEmpty()) {
            throw failure(LLMJsonException.Code.JSON_SHAPE, request, definition, text, schema, mismatch, null);
        }
        try {
            return mapper.treeToValue(root, type);
        } catch (JacksonException invalid) {
            var fields = new HashSet<String>();
            fieldNames(schema, fields);
            var path = invalid.getPath().stream().limit(12)
                    .map(value -> fields.contains(value.getPropertyName()) ? value.getPropertyName() : "[索引或隐藏字段]")
                    .collect(java.util.stream.Collectors.joining("."));
            throw failure(LLMJsonException.Code.JSON_BINDING, request, definition, text, schema,
                    "字段=" + (path.isEmpty() ? "$" : path) + "；类型转换或领域构造校验失败", invalid);
        }
    }

    /**
     * 按已编译 Schema 检查 JSON 节点，不扫描 Java 成员。
     * @param node 实际节点
     * @param schema 固定结构
     * @param path 只含宿主字段与数组索引的路径
     * @param depth 当前深度
     * @return 第一处安全不匹配原因；空字符串表示通过
     */
    private static String validate(@Nullable JsonNode node, JsonNode schema, String path, int depth) {
        if (depth > 24) { return path + "：嵌套超出限制"; }
        if (schema.has("anyOf")) {
            for (var branch : schema.path("anyOf")) {
                if (validate(node, branch, path, depth + 1).isEmpty()) { return ""; }
            }
            return path + "：类型不匹配，允许声明类型或 null";
        }
        String type = schema.path("type").stringValue();
        boolean matches = node != null && switch (type) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "string" -> node.isString();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "null" -> node.isNull();
            default -> false;
        };
        if (!matches) { return path + "：要求 " + type + "，实际=" + (node == null ? "空输出" : node.getNodeType()); }
        if ("object".equals(type)) {
            var properties = schema.path("properties");
            for (var name : properties.propertyNames()) {
                if (!node.has(name)) { return path + "." + name + "：缺少必需字段"; }
                var issue = validate(node.path(name), properties.path(name), path + "." + name, depth + 1);
                if (!issue.isEmpty()) { return issue; }
            }
            if (node.size() != properties.size()) { return path + "：含未声明的额外字段"; }
        } else if ("array".equals(type)) {
            for (int index = 0; index < node.size(); index++) {
                var issue = validate(node.path(index), schema.path("items"), path + "[" + index + "]", depth + 1);
                if (!issue.isEmpty()) { return issue; }
            }
        }
        if (schema.has("enum")) {
            for (var allowed : schema.path("enum")) {
                if (allowed.equals(node)) { return ""; }
            }
            return path + "：不在声明的枚举值中";
        }
        return "";
    }

    /**
     * 生成安全失败，不挂接可能包含原文的解析异常。
     * @param code 失败阶段
     * @param request 原请求编号；未取得时不编造编号
     * @param definition 固定合同
     * @param text 原输出，仅用于摘要和脱敏结构
     * @param schema 固定结构树
     * @param detail 已脱敏定位
     * @param cause 可选解析异常，只提取结构化诊断
     * @return 安全异常
     */
    private static LLMJsonException failure(LLMJsonException.Code code, @Nullable UUID request, LLMJsonSchemas.Definition definition,
            String text, JsonNode schema, String detail, @Nullable JacksonException cause) {
        var fields = new HashSet<String>();
        fieldNames(schema, fields);
        return new LLMJsonException(code, LLMInteractionResult.Status.SUCCESS, request,
                "结构=" + definition.schema().name() + "，" + detail + "，输出字符数=" + text.length()
                        + "，输出摘要=" + StableDigest.sha256(text).substring(0, 16)
                        + "，原输出结构（字段值已隐藏）=" + shape(text, fields),
                cause == null ? null : FailureDiagnostic.from(cause));
    }

    /**
     * 收集宿主已声明字段，未知模型字符串不能借诊断回显。
     * @param schema 固定 Schema
     * @param fields 只含声明字段的集合
     */
    private static void fieldNames(JsonNode schema, Set<String> fields) {
        if (schema.isObject()) {
            fields.addAll(schema.path("properties").propertyNames());
        }
        for (var child : schema) {
            if (child.isContainer()) { fieldNames(child, fields); }
        }
    }

    /**
     * 保留 JSON 标点和已知字段；字符串值、未引号文本及数字均不公开。
     * @param text 原输出
     * @param fields 宿主字段白名单
     * @return 至多 640 字符的结构预览，不是修复后的 JSON
     */
    private static String shape(String text, Set<String> fields) {
        var out = new StringBuilder();
        int limit = Math.min(text.length(), 4096);
        for (int index = 0; index < limit && out.length() < 600;) {
            char current = text.charAt(index++);
            if (current == '"') {
                int start = index;
                boolean escaped = false;
                while (index < limit) {
                    char next = text.charAt(index++);
                    if (next == '"' && !escaped) { break; }
                    escaped = next == '\\' && !escaped;
                }
                boolean closed = index > start && text.charAt(index - 1) == '"';
                var token = text.substring(start, closed ? index - 1 : index);
                int after = index;
                while (after < limit && Character.isWhitespace(text.charAt(after))) { after++; }
                boolean key = closed && after < limit && text.charAt(after) == ':' && fields.contains(token);
                out.append('"').append(key ? token : "…").append(closed ? "\"" : "[未闭合]");
            } else if ("{}[]:,".indexOf(current) >= 0) {
                out.append(current);
            } else if (!Character.isWhitespace(current)) {
                while (index < limit && "{}[]:,\"".indexOf(text.charAt(index)) < 0
                        && !Character.isWhitespace(text.charAt(index))) { index++; }
                out.append('…');
            }
        }
        if (text.length() > limit || out.length() >= 600) { out.append("[后续省略]"); }
        return out.toString();
    }
}
