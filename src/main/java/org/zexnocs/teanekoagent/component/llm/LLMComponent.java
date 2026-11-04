package org.zexnocs.teanekoagent.component.llm;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMOptionBoundary;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 必需的模型组件，选择系统路由或数据库私有配置，凭据不进入运行参数组件。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "llm", name = "大语言模型", description = "必要组件；选择系统模型或仅当前聊天保存的私有模型。")
@PluginOwned("teaneko.agent")
public final class LLMComponent extends DeclaredAgentComponent {
    private final org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser parser;
    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).build();

    /**
     * 创建不包含任何秘密的系统模型模板。
     * @param parser Core 环境变量解析器，只在受保护入口展开配置
     */
    public LLMComponent(org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser parser) {
        super("llm", Map.of("source", "system", "route", ""), List.of(
                new Field("source", "模型来源", "system 使用系统路由；custom 使用当前聊天私有配置。", "TEXT"),
                new Field("route", "系统模型路由", "留空继承系统默认，只有 system 模式使用。", "TEXT"),
                new Field("configuration", "私有模型配置", "与 model.yml 单条模型字段一致，凭据不回显。", "JSON")));
        this.parser = parser;
    }

    /**
     * @param parameters 本次组件参数
     * @throws IllegalArgumentException 当来源、路由、额度或私有配置无效时
     */
    @Override
    public void validate(JsonNode parameters) {
        super.validate(parameters);
        String source = parameters.path("source").asString("system");
        if (source.equals("system")) {
            String route = parameters.path("route").asString("");
            if ((!route.isEmpty() && !route.matches("[a-z][a-z0-9-]{0,63}")) || parameters.has("configuration")) {
                throw new IllegalArgumentException("系统模型组件只接受已配置路由，不接受私有凭据。");
            }
        } else if (source.equals("custom")) {
            var config = resolveConfiguration(parameters);
            var options = config.toOptions(LLMModelOptions.builder().provider(config.adapterId().id()).build());
            LLMOptionBoundary.requireResolved(options);
            new LLMProviderAccess(config.getApiKey(), config.getBaseUrl(), config.getApiPath());
        } else {
            throw new IllegalArgumentException("LLM 组件来源只能是 system 或 custom。");
        }
    }

    /**
     * 只在受保护配置或实际凭据边界转换私有模型，不返回给公共管理目录。
     * @param parameters 含私有模型的原参数树
     * @return 明确配置，字段采用与模型文件相同的连字符形式
     */
    public static LLMModelFileConfigParameter configuration(JsonNode parameters) {
        if (!parameters.path("configuration").isObject()) {
            throw new IllegalArgumentException("私有模型需要完整的 configuration 对象。");
        }
        try {
            var value = JSON.treeToValue(parameters.get("configuration"), LLMModelFileConfigParameter.class);
            if (value.getAdapter() == null || value.getAdapter().isBlank()) {
                throw new IllegalArgumentException("私有模型必须声明协议适配器。");
            }
            value.setId(value.getAdapter());
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("私有模型配置无效，请检查协议、型号、连接和额度。");
        }
    }

    /**
     * @param parameters 受保护的原参数树
     * @return 使用 Core 统一展开规则解析的当前配置，不回写模板
     */
    public LLMModelFileConfigParameter resolveConfiguration(JsonNode parameters) {
        return configuration(parser.resolveDocument(parameters));
    }
}
