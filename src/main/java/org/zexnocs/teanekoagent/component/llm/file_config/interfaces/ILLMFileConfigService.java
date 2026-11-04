package org.zexnocs.teanekoagent.component.llm.file_config.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;

/**
 * 大语言模型文件配置服务接口。
 * <br>用于读取 agent 模型文件配置，并按模型适配器 ID 构造默认调用参数。
 * <br>2026/11/04：明确基础参数可空，保持独立调用与文件路由合并契约一致。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 * @version 4.8.0-l
 */
public interface ILLMFileConfigService {
    /**
     * 获取 LLM 模型文件配置。
     *
     * @return LLM 模型文件配置
     */
    LLMModelFileConfig getConfig();

    /**
     * 根据模型适配器 ID 和模型 base options 构造文件默认 options。
     *
     * @param modelId 模型适配器 ID
     * @param baseOptions 模型代码自带的 base options，空值表示不提供基础参数
     * @return 合并后的文件默认 options
     */
    // 2026/11/04：允许独立调用方不提供基础参数，与现有合并行为保持一致。
    LLMModelOptions getDefaultOptions(LLMModelId modelId, @Nullable ILLMModelOptions baseOptions);
}
