package org.zexnocs.teanekoagent.llm.file_config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
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
 * @date 2026/06/08
 * @since 4.4.0
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
     * <br>优先读取 {@code config/agent/model.yml}；当配置尚未加载时返回默认配置对象，
     * 避免配置项缺失影响模型注册流程。
     *
     * @return LLM 模型文件配置
     */
    @Override
    public LLMModelFileConfig getConfig() {
        try {
            return fileConfigService.get(LLMModelFileConfig.class);
        } catch (FileConfigDataNotFoundException ignored) {
            return new LLMModelFileConfig();
        }
    }

    /**
     * 根据模型适配器 ID 和代码默认 options 构造实际默认 options。
     * <br>模型适配器 ID 通常等于供应商 ID，具体模型名称优先来自代码默认 options，其次可由文件配置中的 {@code model} 覆盖。
     *
     * @param modelId 模型适配器 ID
     * @param codeDefaults 代码默认 options
     * @return 合并后的默认 options
     */
    @Override
    public LLMModelOptions getDefaultOptions(LLMModelId modelId, ILLMModelOptions codeDefaults) {
        var base = LLMModelOptions.merge(
                codeDefaults,
                LLMModelOptions.builder()
                        .provider(modelId.id())
                        .build()
        );
        return getConfig()
                .findModelConfig(modelId)
                .map(modelConfig -> modelConfig.toOptions(base))
                .orElse(base);
    }
}
