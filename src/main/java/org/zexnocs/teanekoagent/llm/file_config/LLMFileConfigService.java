package org.zexnocs.teanekoagent.llm.file_config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 大语言模型文件配置服务。
 * <br>负责把 {@link LLMModelFileConfig} 中的配置项合并为框架统一的 {@link LLMModelOptions}。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
@Service
@RequiredArgsConstructor
public class LLMFileConfigService implements ILLMFileConfigService {
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
    public LLMModelOptions getDefaultOptions(LLMModelId modelId, ILLMModelOptions baseOptions) {
        var base = LLMModelOptions.merge(
                baseOptions,
                LLMModelOptions.builder()
                        .provider(modelId.id())
                        .build()
        );
        var entry = getConfig()
                .findModelConfig(modelId)
                .orElseThrow(() -> new LLMConfigurationException("Selected LLM route has no entry in model.yml."));
        if (entry.getModel() == null || entry.getModel().isBlank()) {
            throw new LLMConfigurationException("Selected LLM route requires an explicit model in model.yml.");
        }
        return entry.toOptions(base);
    }
}
