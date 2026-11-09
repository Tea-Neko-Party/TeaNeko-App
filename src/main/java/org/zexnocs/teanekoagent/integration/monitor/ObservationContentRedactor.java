package org.zexnocs.teanekoagent.integration.monitor;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 单次请求内共享的有界内容脱敏器；结构化内容按键处理，编码字符串逐层检查。
 * <br>2026/11/06：完整隐藏授权值及嵌套 JSON 凭据，无法确定边界的敏感内容不回退原文。
 *
 * <br>2026/11/08：独立诊断同样隐藏供应商加密状态，保留可读文本字段。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.9.0-l
 */
final class ObservationContentRedactor {
    private static final String HIDDEN = "[敏感内容已隐藏]";
    private static final String LIMITED = "[内容已隐藏：超出脱敏处理上限]";
    private static final int MAX_INPUT = 2_000_000;
    private static final int MAX_ENCODED_DEPTH = 4;
    private static final String KEYS = "(?:x[-_]?)?api[-_]?key|access[-_]?token|refresh[-_]?token|token|password"
            + "|client[-_]?secret|secret|(?:proxy[-_])?authorization|cookie|set[-_]cookie|rkey|signature|encrypted[-_]?content|encrypted[-_]?state";
    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i)^(?:" + KEYS + ")$");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_-])([\"']?(" + KEYS + ")[\"']?\\s*[:=]\\s*)");
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"(?:[^\"\\\\]++|\\\\.)*+\"|'(?:[^'\\\\]++|\\\\.)*+'");
    private static final Pattern ENCODED_FRAGMENT = Pattern.compile("\\\\[\"'/uU]");
    private static final Pattern LINK = Pattern.compile("(?i)(?:https?|wss?)://[^\\s\"'<>]+|data:[^\\s\"'<>]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[^\\s\"',;]+|\\bsk-[A-Za-z0-9_-]+");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(
                    StreamReadConstraints.builder().maxDocumentLength(MAX_INPUT).maxStringLength(MAX_INPUT)
                            .maxNameLength(8192).maxNestingDepth(32).maxTokenCount(80_000).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private int remainingCharacters = 8_000_000;
    private int remainingNodes = 40_000;
    private boolean exhausted;

    /** 为一条请求建立独立处理预算，不保留其他请求的正文或处理状态。 */
    ObservationContentRedactor() {}

    /**
     * 先处理完整内容，再由外层格式器截断；超限时只返回说明，不能暴露未处理片段。
     * @param value 本次正文、工具参数、回执或供应商推理
     * @return 已脱敏内容或明确的隐藏标记
     */
    String redact(String value) {
        var safe = redact(value, 0);
        return exhausted ? LIMITED : safe;
    }

    /**
     * 对完整 JSON 按结构处理，对普通文本保留可确认的非敏感部分。
     * @param value 待检查文本
     * @param encodedDepth 已展开的 JSON 编码层数
     * @return 可输出文本；解析或处理上限不泄漏原文
     */
    private String redact(String value, int encodedDepth) {
        if (value.length() > MAX_INPUT || exhausted) {
            return LIMITED;
        }
        remainingCharacters -= value.length();
        if (remainingCharacters < 0) {
            exhausted = true;
            return LIMITED;
        }
        var trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "{[\"".indexOf(trimmed.charAt(0)) >= 0) {
            if (encodedDepth >= MAX_ENCODED_DEPTH) {
                return LIMITED;
            }
            try {
                var tree = JSON.readTree(value);
                var safe = redact(tree, encodedDepth + 1);
                return exhausted ? LIMITED : JSON.writeValueAsString(safe);
            } catch (StreamConstraintsException limit) {
                return LIMITED;
            } catch (JacksonException invalid) {
                // 2026/11/06：混合正文可能不是完整 JSON；只允许经过保守文本处理后输出。
                return text(value);
            }
        }
        return text(value);
    }

    /**
     * 敏感键隐藏整个值，包括对象和数组；字符串里的内层 JSON 继续受同一预算约束。
     * @param node 已通过语法和解析深度检查的节点
     * @param encodedDepth 已解码层数
     * @return 新的安全节点，不改变调用方或业务模型对象
     */
    private JsonNode redact(JsonNode node, int encodedDepth) {
        if (exhausted || --remainingNodes < 0) {
            exhausted = true;
            return StringNode.valueOf(LIMITED);
        }
        if (node.isObject()) {
            var safe = JSON.createObjectNode();
            for (var key : node.propertyNames()) {
                safe.set(text(key), SENSITIVE_KEY.matcher(key).matches() ? StringNode.valueOf(HIDDEN)
                        : redact(node.path(key), encodedDepth));
                if (exhausted) {
                    return StringNode.valueOf(LIMITED);
                }
            }
            return safe;
        }
        if (node.isArray()) {
            var safe = JSON.createArrayNode();
            for (var item : node) {
                safe.add(redact(item, encodedDepth));
                if (exhausted) {
                    return StringNode.valueOf(LIMITED);
                }
            }
            return safe;
        }
        return node.isString() ? StringNode.valueOf(redact(node.stringValue(), encodedDepth)) : node;
    }

    /**
     * 授权头隐藏到行末；普通密钥仅在引号完整时保留后文，否则隐藏本行余下内容。
     * 残留的 JSON 转义表示不能可靠判定内层语义，整段隐藏，不靠正则猜测解码值。
     * @param value 未编码文本或不能完整解析的混合正文
     * @return 已隐藏全部可疑授权值、媒体及编码片段的文本
     */
    private static String text(String value) {
        var fields = KEY_VALUE.matcher(value);
        var safe = new StringBuilder();
        int offset = 0;
        while (fields.find(offset)) {
            safe.append(value, offset, fields.end()).append(HIDDEN);
            var name = fields.group(2).toLowerCase(Locale.ROOT);
            boolean header = name.contains("authorization") || name.contains("cookie");
            var quoted = QUOTED_VALUE.matcher(value).region(fields.end(), value.length());
            if (!header && quoted.lookingAt()) {
                offset = quoted.end();
            } else if (fields.end() < value.length() && "{[\"'".indexOf(value.charAt(fields.end())) >= 0) {
                // 2026/11/06：畸形对象或未闭合引号可能跨行，无法划定边界时隐藏整个字段。
                return HIDDEN;
            } else {
                // 2026/11/06：内联 Cookie 的分号不是授权值结束，不能留下后续会话凭据。
                offset = valueEnd(value, fields.end(), header);
            }
        }
        safe.append(value, offset, value.length());
        if (ENCODED_FRAGMENT.matcher(safe).find()) {
            return HIDDEN;
        }
        var result = BEARER.matcher(safe).replaceAll(HIDDEN);
        return LINK.matcher(result).replaceAll("[链接或内联数据已隐藏]");
    }

    /**
     * 2026/11/06：授权头的缩进续行仍属于同一授权值，不能在换行后留下会话凭据。
     * @param value 原文本
     * @param offset 授权值或普通未加引号字段的起点
     * @param header 是否需要消费授权头的续行
     * @return 完整值末尾，普通下一行仍可继续脱敏
     */
    private static int valueEnd(String value, int offset, boolean header) {
        while (true) {
            while (offset < value.length() && value.charAt(offset) != '\n' && value.charAt(offset) != '\r') {
                offset++;
            }
            int next = offset;
            if (next < value.length() && value.charAt(next) == '\r') {
                next++;
            }
            if (next < value.length() && value.charAt(next) == '\n') {
                next++;
            }
            if (!header || next >= value.length() || value.charAt(next) != ' ' && value.charAt(next) != '\t') {
                return offset;
            }
            offset = next;
        }
    }
}
