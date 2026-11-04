package org.zexnocs.teanekoagent.component.llm.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.model.*;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.LLMResponseFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单个大语言模型适配器的文件配置项。
 * <br>用于把 YAML 中的默认调用参数转换为统一的 {@link LLMModelOptions}。
 * <br>2026/11/04：显式声明模型上下文容量，输入比例默认百分之八十，输出额度统一取本配置。
 * <br>新增一个供应商适配器时，通常只需要在 {@code models} 列表中添加一项：
 * <pre>{@code
 * models:
 *   - id: "deepseek"
 *     model: "deepseek-v4-flash"
 *     max-tokens: 2048
 *     context-window-tokens: "${MODEL_CONTEXT_WINDOW_TOKENS}"
 *     context-input-ratio: 0.8
 * }</pre>
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * <br>2026/11/04：为模型参数提供可由控制台读取的用途和单位。
 *
 * <br>2026/11/04：分离唯一配置路由和协议适配器，同协议可配置多个模型及密钥。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.0
 * @version 4.8.0-l
 */
@Getter
@Setter
@NoArgsConstructor
// 2026/11/04：控制台展示类型及字段注解，不读取运行秘密。
@org.zexnocs.teanekocore.framework.description.Description("模型路由参数；凭据和连接不通过普通序列化导出。")
public class LLMModelFileConfigParameter {
    /**
     * 唯一配置路由 ID；同一适配器可以通过不同路由保存多个模型或密钥。
     */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("唯一模型配置路由；可自定义，不等于供应商模型名称。")
    private String id;

    // 2026/11/04：配置路由与 Spring 协议适配器分离；旧文件省略时沿用原路由同名适配器。
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("已注册的协议适配器，例如 deepseek、kimi；省略时使用路由标识。")
    private String adapter;

    /**
     * 模型供应商标识。
     * <br>该字段仅作为 {@link #id} 未设置时的兼容写法；推荐直接使用 {@link #id}。
     */
    @Nullable
    private String provider;

    /**
     * 默认模型名称。
     * <br>该字段写入 {@link LLMModelOptions#getModel()}，用于覆盖模型适配器代码中的默认模型名，例如把 {@code deepseek-v4-flash} 改为其他兼容模型。
     * <br>该字段不参与模型适配器路由，路由只由 {@link #id} 决定。
     */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("具体模型标识；选择时核对真实上下文容量。")
    private String model;

    /**
     * 请求接口路径，允许环境变量占位符；api 只作为旧 YAML 字段名的兼容别名。
     */
    @Nullable
    @JsonAlias("api")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @org.zexnocs.teanekocore.framework.description.Description("相对于服务基础地址的接口路径，例如 /chat/completions。")
    private String apiPath;

    /**
     * 原始密钥或环境变量占位符，只由可信凭据端口解析，不写入 Options 或通用导出。
     */
    @Nullable
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @org.zexnocs.teanekocore.framework.description.Description("调用凭据；支持环境占位符，不回显原值。")
    private String apiKey;

    /**
     * 原始服务地址或环境变量占位符，默认留空使用适配器内建值。
     */
    @Nullable
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @org.zexnocs.teanekocore.framework.description.Description("服务基础地址；与接口路径组合为请求地址。")
    private String baseUrl;

    /**
     * 是否启用思考或推理模式。
     */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("是否请求思考模式，实际能力由供应商与型号决定。")
    private Boolean thinking;

    /**
     * 模型最多生成的 token 数量。
     */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("每次请求完整的最大输出 token 额度，不另设业务隐含上限。")
    private Integer maxTokens;

    /** 2026/11/04：部署模型的真实上下文总容量，不能从模型名称推测。 */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("实际型号的上下文总容量，计入输入与输出。")
    private Long contextWindowTokens;

    /** 2026/11/04：输入占上下文的最大比例，未填写时使用百分之八十。 */
    @Nullable
    @org.zexnocs.teanekocore.framework.description.Description("输入最多占上下文的比例，默认 80%，仍为完整输出预留空间。")
    private Double contextInputRatio;

    /**
     * 频率惩罚值。
     */
    @Nullable
    private Double frequencyPenalty;

    /**
     * 采样温度。
     */
    @Nullable
    private Double temperature;

    /**
     * nucleus sampling 的 top-p 参数。
     */
    @Nullable
    private Double topP;

    /**
     * 存在惩罚值。
     */
    @Nullable
    private Double presencePenalty;

    /**
     * 模型输出格式。
     */
    @Nullable
    private LLMResponseFormat responseFormat;

    /**
     * 停止词列表。
     */
    @Nullable
    private List<String> stopWords;

    /**
     * 是否启用流式响应。
     */
    @Nullable
    private Boolean stream;

    /**
     * 工具调用策略。
     */
    @Nullable
    private String toolChoice;

    /**
     * 是否要求模型返回 token 的对数概率信息。
     */
    @Nullable
    private Boolean logprobs;

    /**
     * 供应商扩展参数。
     * <br>不属于通用 options 的字段应放在这里，供应商适配器按约定读取。
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * 查找该配置项对应的模型适配器 ID。
     * <br>优先使用 {@link #id}；当 {@link #id} 未设置时，使用 {@link #provider} 作为兼容回退。
     *
     * @return 模型适配器 ID
     */
    public Optional<LLMModelId> findModelId() {
        var parsedId = LLMModelFileConfig.parseModelId(id);
        if (parsedId.isPresent()) {
            return parsedId;
        }
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(LLMModelId.of(provider));
    }

    /**
     * 2026/11/04：将唯一配置路由解析为真实协议，旧配置保持同名含义。
     * @return 实际协议适配器标识
     * @throws LLMConfigurationException 当路由缺失或适配器标识非法时
     */
    public LLMModelId adapterId() {
        return adapter == null || adapter.isBlank()
                ? findModelId().orElseThrow(() -> new LLMConfigurationException("模型配置缺少路由标识。"))
                : LLMModelId.of(adapter);
    }

    /**
     * 将该配置项转换为统一模型调用参数。
     * <br>转换时会先保留模型 base options，再用文件 default options 覆盖已声明字段。
     *
     * @param baseOptions 模型代码中声明的 base options
     * @return 合并后的模型调用参数
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    public LLMModelOptions toOptions(@Nullable ILLMModelOptions baseOptions) {
        // 2026/11/04：选定模型必须提供完整容量声明，禁止回退为隐含输出常量。
        if (maxTokens == null || maxTokens < 1 || contextWindowTokens == null) {
            throw new LLMConfigurationException("模型配置必须填写正数 max-tokens 和 context-window-tokens。");
        }
        var context = new LLMContextWindow(contextWindowTokens,
                contextInputRatio == null ? LLMContextWindow.DEFAULT_INPUT_RATIO : contextInputRatio);
        context.maxInputTokens(maxTokens);
        LLMOptionBoundary.validateMetadata(metadata);
        if (metadata != null && metadata.keySet().stream().anyMatch(key ->
                key != null && switch (key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")) {
                    case "api", "apipath", "baseurl", "endpoint" -> true;
                    default -> false;
                })) {
            throw new LLMConfigurationException("连接配置须使用 model.yml 条目的 api-path/base-url，不能写入模型 metadata。");
        }
        var modelId = findModelId();
        var mergedMetadata = new LinkedHashMap<String, Object>();
        if (baseOptions != null) {
            baseOptions.findMetadata().ifPresent(mergedMetadata::putAll);
        }
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        var overrides = LLMModelOptions.builder()
                .provider(modelId.map(LLMModelId::id).orElse(blankToNull(provider)))
                .model(blankToNull(model))
                .thinking(thinking)
                .maxTokens(maxTokens)
                .contextWindow(context)
                .frequencyPenalty(frequencyPenalty)
                .temperature(temperature)
                .topP(topP)
                .presencePenalty(presencePenalty)
                .responseFormat(responseFormat)
                .stopWords(stopWords)
                .stream(stream)
                .toolChoice(blankToNull(toolChoice))
                .logprobs(logprobs)
                .metadata(new LinkedHashMap<>(mergedMetadata))
                .build();
        return LLMModelOptions.merge(baseOptions, overrides);
    }

    /**
     * 将空白字符串视为未配置。
     *
     * @param value 配置值
     * @return 非空白字符串；空白时返回 {@code null}
     */
    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
