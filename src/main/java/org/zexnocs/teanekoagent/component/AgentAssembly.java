package org.zexnocs.teanekoagent.component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 不含凭据的不可变装配快照，组件参数以独立 JSON 保存，版本指纹参与在途策略复核。
 * 持久配置原文只在受保护入口处理，运行快照不持有可修改的 JSON 节点。
 *
 * 修改记录：<br>
 * 2026/11/04：建立版本化组件序列化协议与新实例的最小装配。
 *
 * @param version 装配协议版本
 * @param parameters 各组件独立参数的不可变 JSON 文本
 * @param revision 含秘密原配置的摘要，不导出原配置
 * @param legacy 是否来自历史未声明装配的配置
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public record AgentAssembly(int version, Map<String, String> parameters, String revision, boolean legacy) {
    public static final String MINIMAL = "{\"version\":1,\"components\":{\"llm\":{\"source\":\"system\",\"route\":\"\"}}}";
    private static final ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> SECRETS = Set.of("api-key", "apiKey", "base-url", "baseUrl", "api-path", "apiPath");

    /**
     * 固定组件参数表，拒绝不支持的协议版本。
     * @param version 装配协议
     * @param parameters 只读组件参数
     * @param revision 配置摘要
     * @param legacy 是否旧装配
     */
    public AgentAssembly {
        if (version != 1) {
            throw new IllegalArgumentException("Agent 装配版本不受支持。");
        }
        parameters = Map.copyOf(parameters);
    }

    /**
     * 解析有界装配文本；只检查结构，组件参数由对应注册实现继续验证。
     * @param serialized 明确提交的 JSON
     * @return 新建的配置树，只能由受保护配置入口使用
     * @throws IllegalArgumentException 当文本、版本或组件结构无效时
     */
    public static ObjectNode document(@Nullable String serialized) {
        if (serialized == null || serialized.length() > 131072) {
            throw new IllegalArgumentException("Agent 装配必须是最多 128 KiB 的 JSON 文本。");
        }
        final JsonNode tree;
        try {
            tree = JSON.readTree(serialized);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Agent 装配 JSON 格式无效。");
        }
        if (!(tree instanceof ObjectNode root) || root.size() != 2 || !root.path("version").isInt()
                || root.path("version").asInt() != 1 || !root.path("components").isObject()
                || root.path("components").size() > 32 || !root.path("components").has("llm")) {
            throw new IllegalArgumentException("Agent 装配需要 version=1、components 对象及必要的 llm 组件。");
        }
        for (var entry : root.path("components").properties()) {
            if (!entry.getKey().matches("[a-z][a-z0-9.-]{0,63}") || !entry.getValue().isObject()) {
                throw new IllegalArgumentException("组件标识或参数对象无效。");
            }
        }
        return root;
    }

    /**
     * 创建脱敏运行快照，密钥与连接只保留摘要以识别配置变化。
     * @param serialized 受保护的原配置
     * @return 不含认证值的运行快照
     */
    public static AgentAssembly parse(String serialized) {
        var root = document(serialized);
        var result = new LinkedHashMap<String, String>();
        for (var entry : root.path("components").properties()) {
            var value = entry.getValue().deepCopy();
            // 2026/11/04：私有连接整体不进入实体，metadata 内也可能带有认证参数。
            if (entry.getKey().equals("llm")) {
                ((ObjectNode) value).remove("configuration");
            }
            redact(value);
            result.put(entry.getKey(), value.toString());
        }
        return new AgentAssembly(1, result, StableDigest.sha256(root.toString()), false);
    }

    /**
     * 对敏感键递归脱敏，避免嵌套模型配置进入公共实体。
     * @param node 本次独立复制的节点
     */
    private static void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (var entry : java.util.List.copyOf(object.properties())) {
                if (SECRETS.contains(entry.getKey())) {
                    object.remove(entry.getKey());
                } else {
                    redact(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(AgentAssembly::redact);
        }
    }

    /** @return 独立旧宿主保持原能力选择，不改变已有构造的运行语义。 */
    public static AgentAssembly legacyDefaults() {
        return new AgentAssembly(1, Map.of(), "legacy", true);
    }

    /**
     * @param id 组件标识
     * @return 当前是否安装；旧宿主沿用原固定装配
     */
    public boolean has(String id) {
        return legacy || parameters.containsKey(id);
    }

    /**
     * @param id 已注册组件标识
     * @return 本次读取专有节点，不允许修改快照
     */
    @JsonIgnore
    public JsonNode settings(String id) {
        return JSON.readTree(parameters.getOrDefault(id, "{}"));
    }
}
