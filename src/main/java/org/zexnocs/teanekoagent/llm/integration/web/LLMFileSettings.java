package org.zexnocs.teanekoagent.llm.integration.web;

// 2026/11/07：系统模型表单归入独立 LLM 子模块，沿用原文件权限、脱敏与事务规则。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMOptionBoundary;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.IFileSettingsAdapter;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.List;
import java.util.Objects;

/**
 * 统一模型文件表单，凭据、连接及扩展参数只以保护字段替换。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：上下文容量与输入比例表单归入 v4.7.2-l。
 * <br>2026/11/04：开放协议适配器字段，校验唯一配置路由及协议标识。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
 * <br>2026/11/05：模型连接地址及路径按普通字段显示，密钥继续隐藏。
 * <br>2026/11/07：模型表单属于系统内置配置，只沿用文件授权，不依赖插件目录或业务插件启停。
 * <br>2026/11/07：表单随独立 LLM 子模块装配，不依赖 Agent 组件或配置类型。
 * <br>2026/11/10：为模型配置表单声明可选图片输入能力字段。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.1-l
 */
@Component
// 2026/11/07：模型协议与凭据由独立 LLM 提供，不投影为可选插件配置。
@TeaNekoFileSettings("agent.models")
public final class LLMFileSettings implements IFileSettingsAdapter {
    private final IFileConfigService files;
    private final ILLMFileConfigService models;
    private final List<FileSettings.Rule> fields;
    /**
     * 创建系统模型表单，字段与秘密操作沿用原模型文件合同。
     * @param files 原文件入口
     * @param models 实际模型参数消费端口
     */
    public LLMFileSettings(IFileConfigService files, ILLMFileConfigService models) {
        this.files = files;
        this.fields = List.of(
                field("/default-model-id", "默认模型路由", "TEXT"), field("/models/*/id", "路由标识", "TEXT"),
                // 2026/11/04：唯一配置路由与实际协议适配器分别编辑。
                field("/models/*/adapter", "协议适配器", "TEXT"),
                field("/models/*/provider", "兼容供应商标识", "TEXT"), field("/models/*/model", "实际模型名称", "TEXT"),
                // 2026/11/05：管理员可读原始地址与路径模板，只有真实凭据继续脱敏。
                secret("/models/*/api-key", "API 密钥", "TEXT"), field("/models/*/base-url", "服务地址", "TEXT"),
                field("/models/*/image-input", "允许图片输入", "BOOLEAN"),
                field("/models/*/api-path", "接口路径", "TEXT"), field("/models/*/api", "旧接口路径别名", "TEXT"),
                field("/models/*/thinking", "启用思考", "BOOLEAN"), field("/models/*/max-tokens", "最大输出 token", "NUMBER"),
                // 2026/11/04：模型容量与默认输入比例和其他模型参数共同保存、应用。
                field("/models/*/context-window-tokens", "上下文总 token 容量", "NUMBER"),
                field("/models/*/context-input-ratio", "输入上下文占比（默认 0.8）", "NUMBER"),
                field("/models/*/temperature", "采样温度", "NUMBER"), field("/models/*/top-p", "概率采样阈值", "NUMBER"),
                field("/models/*/frequency-penalty", "频率惩罚", "NUMBER"), field("/models/*/presence-penalty", "存在惩罚", "NUMBER"),
                field("/models/*/response-format", "响应格式", "JSON"), field("/models/*/stop-words", "停止词", "JSON"),
                field("/models/*/stream", "流式响应选项", "BOOLEAN"), field("/models/*/tool-choice", "工具选择策略", "TEXT"),
                field("/models/*/logprobs", "返回概率信息", "BOOLEAN"), secret("/models/*/metadata", "扩展参数 metadata", "JSON"));
        this.models = models;
    }
    /** @return 路由数组的真实增删能力 */
    @Override
    public List<FileSettings.CollectionRule> collections() {
        return List.of(new FileSettings.CollectionRule("/models", "模型路由", 64));
    }
    /**
     * 在模板展开之前检查旧别名与正式路径的冲突。
     * @param document 未展开候选
     * @throws IllegalArgumentException 当同一模型同时声明旧接口别名与正式路径时
     */
    @Override
    public void validateDocument(tools.jackson.databind.JsonNode document) {
        for (var model : document.path("models")) {
            if (model.hasNonNull("api") && model.hasNonNull("api-path")) {
                throw new IllegalArgumentException("旧接口路径别名与 api-path 不能同时声明，请明确清除其中一个。");
            }
        }
    }
    /**
     * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * @param candidate 原文件候选
     * @throws IllegalArgumentException 当模型路由、参数、数量或默认选择无效时
     */
    @Override
    public void validate(IFileConfigData candidate) {
        var file = (LLMModelFileConfig) candidate;
        var values = Objects.requireNonNull(file.getModels());
        if (values.size() > 64 || file.getDefaultModelId() == null
                || !file.getDefaultModelId().isBlank() && file.findDefaultModelId().isEmpty()) {
            throw new IllegalArgumentException("模型路由数量或默认路由无效。");
        }
        for (var entry : values) {
            var id = Objects.requireNonNull(entry).findModelId().orElseThrow();
            file.findModelConfig(id).orElseThrow();
            var adapter = entry.adapterId(); // 2026/11/04：路由与协议独立校验，同协议可以配置多条。
            if (entry.getModel() == null || entry.getModel().isBlank()) {
                throw new IllegalArgumentException("模型条目必须填写实际模型名称。");
            }
            LLMOptionBoundary.validate(entry.toOptions(LLMModelOptions.builder().provider(adapter.id()).model(entry.getModel()).build()));
            new LLMProviderAccess(entry.getApiKey() == null || entry.getApiKey().isBlank() ? "仅本地格式校验" : entry.getApiKey(),
                    entry.getBaseUrl(), entry.getApiPath());
        }
        file.findDefaultModelId().ifPresent(id -> file.findModelConfig(id).orElseThrow());
    }
    /**
     * 确认文件发布与模型参数端口读取同一快照，不发起网络检测。
     * @param published 原候选
     * @return 实际模型配置消费端口的确认，不进行供应商请求
     * @throws IllegalStateException 当文件缓存或参数端口未读取原发布快照时
     */
    @Override
    public String verifyApplied(IFileConfigData published) {
        if (files.requireCurrent(LLMModelFileConfig.class) != published) {
            throw new IllegalStateException("模型文件缓存已被其他发布替换。");
        }
        if (models.getConfig() != published) {
            throw new IllegalStateException("模型参数端口没有读取到新快照。");
        }
        return "模型配置端口已读取新快照；旧已解析参数在换代后拒绝使用新凭据，已构造的 HTTP 请求保留原凭据。未发起模型请求验证密钥。";
    }

    /** @return 系统模型配置的中文名称 */
    @Override
    public String name() {
        return "模型、凭据与连接";
    }

    /** @return 独立模型文件的固定类型 */
    @Override
    public Class<? extends IFileConfigData> type() {
        return LLMModelFileConfig.class;
    }

    /** @return 模型字段的明确编辑规则 */
    @Override
    public List<FileSettings.Rule> fields() {
        return fields;
    }

    /** @return 新请求读取已经确认的配置快照 */
    @Override
    public String application() {
        return "NEXT_USE";
    }

    /**
     * 为普通模型参数声明固定表单，不自动开放其他配置属性。
     * @param path 模型文件字段
     * @param label 中文名称
     * @param kind 明确编辑类型
     * @return 非敏感字段规则
     */
    private static FileSettings.Rule field(String path, String label, String kind) {
        return new FileSettings.Rule(path, label, "保留原环境模板；设置后使用原模块范围校验。", kind, false);
    }

    /**
     * 将凭据限定为显式替换或清除，读取时不返回原值。
     * @param path 受保护字段
     * @param label 中文名称
     * @param kind 明确编辑类型
     * @return 秘密字段规则
     */
    private static FileSettings.Rule secret(String path, String label, String kind) {
        return new FileSettings.Rule(path, label,
                "不返回原值或展开值；留在保留状态不会改写，替换可填写环境占位符。", kind, true);
    }
}
