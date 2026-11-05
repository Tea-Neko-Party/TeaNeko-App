package org.zexnocs.teanekoagent.component.llm.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 大语言模型文件配置服务。
 * <br>负责把 {@link LLMModelFileConfig} 中的配置项合并为框架统一的 {@link LLMModelOptions}。
 * <br>2026/11/03：文件默认参数记录发布代次，支持安全热应用。
 *
 * <br>2026/11/04：按配置路由合并参数，并检查其真实协议与基础参数一致。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：路由身份覆盖保留基础 JSON 选项。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.8.3-l
 */
@Service
@RequiredArgsConstructor
public class LLMFileConfigService implements ILLMFileConfigService {
    private @org.jspecify.annotations.Nullable LLMFileConfigurationEpoch epoch;

    /**
     * 2026/11/03：Spring 宿主安装文件代次，独立旧构造保持可用。
     * @param epoch 原文件锁与版本边界
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installEpoch(LLMFileConfigurationEpoch epoch) {
        this.epoch = epoch;
    }
    /**
     * TeaNeko Core 文件配置服务。
     */
    private final IFileConfigService fileConfigService;

    /**
     * 获取 LLM 模型文件配置。
     * <br>配置不可用时仅阻止本次调用，不在 Bean 构造或模型注册阶段读取。
     *
     * @return LLM 模型文件配置
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public LLMModelFileConfig getConfig() {
        try {
            return fileConfigService.get(LLMModelFileConfig.class);
        } catch (FileConfigDataNotFoundException ignored) {
            throw new LLMConfigurationException("LLM model.yml is unavailable; reload valid local configuration.");
        }
    }

    /**
     * 根据模型适配器 ID 和模型 base options 构造文件默认 options。
     * <br>模型适配器 ID 通常等于供应商 ID，具体模型名称优先来自 base options，其次可由文件配置中的 {@code model} 覆盖。
     *
     * @param modelId 模型适配器 ID
     * @param baseOptions 模型代码自带的 base options
     * @return 合并后的文件默认 options
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public LLMModelOptions getDefaultOptions(LLMModelId modelId, @org.jspecify.annotations.Nullable ILLMModelOptions baseOptions) {
        // 2026/11/03：参数和标记来自同一缓存代次，不把凭据放入 Options。
        return epoch == null ? defaults(modelId, baseOptions)
                : epoch.fixed(() -> epoch.stamp(defaults(modelId, baseOptions)));
    }

    /**
     * @param modelId 原供应商
     * @param baseOptions 原内建参数
     * @return 文件默认参数
     */
    private LLMModelOptions defaults(LLMModelId modelId, @org.jspecify.annotations.Nullable ILLMModelOptions baseOptions) {
        // 2026/11/06：路由只修改 provider，不重置基础 JSON 格式。
        var base = LLMModelOptions.copyOf(baseOptions);
        base.setProvider(modelId.id());
        var entry = getConfig()
                .findModelConfig(modelId)
                .orElseThrow(() -> new LLMConfigurationException("Selected LLM route has no entry in model.yml."));
        // 2026/11/04：禁止配置路由冒用另一个已注册协议，参数、能力和凭据须属于同一适配器。
        if (baseOptions != null && baseOptions.findProvider().isPresent()
                && !entry.adapterId().id().equals(baseOptions.findProvider().orElseThrow())) {
            throw new LLMConfigurationException("模型配置路由与实际协议适配器不一致。");
        }
        if (entry.getModel() == null || entry.getModel().isBlank()) {
            throw new LLMConfigurationException("Selected LLM route requires an explicit model in model.yml.");
        }
        return entry.toOptions(base);
    }
}
