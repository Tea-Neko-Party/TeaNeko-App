package org.zexnocs.teanekoagent.llm.file_config.interfaces;

import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

/**
 * 大语言模型文件配置服务接口。
 * <br>用于读取 agent 模型文件配置，并按模型适配器 ID 构造默认调用参数。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
public interface ILLMFileConfigService {
    /**
     * 获取 LLM 模型文件配置。
     *
     * @return LLM 模型文件配置
     */
    LLMModelFileConfig getConfig();

    /**
     * 根据模型适配器 ID 和代码默认 options 构造实际默认 options。
     *
     * @param modelId 模型适配器 ID
     * @param codeDefaults 代码默认 options
     * @return 合并后的默认 options
     */
    LLMModelOptions getDefaultOptions(LLMModelId modelId, ILLMModelOptions codeDefaults);
}
