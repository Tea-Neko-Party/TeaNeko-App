package org.zexnocs.teanekoagent.llm.instance.deepseek;

import org.zexnocs.teanekoagent.llm.file_config.form.ILLMConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.file_config.form.LLMConfigurationAdapter;
import java.util.List;

/**
 * DeepSeek 的配置样板，与供应商适配器放在同一目录并复用固定连接常量。
 *
 * 修改记录：<br>
 * 2026/11/04：业务标识与 Spring Bean 名解耦，使用独立 id 声明。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
// 2026/11/04：保持原业务标识，容器按实现类生成 Bean 名。
@LLMConfigurationAdapter(id = DeepSeekChatModel.PROVIDER)
public final class DeepSeekConfigurationAdapter implements ILLMConfigurationAdapter {
    /** @return 默认模型的公开样板，模型、输出及上下文容量均可在页面修改 */
    @Override
    public Template describe() {
        return new Template("DeepSeek", "复用适配器默认连接；更换具体模型或自定义服务后请核对上下文容量。",
                ILLMConfigurationAdapter.defaults(DeepSeekChatModel.PROVIDER, DeepSeekModelOptions.DEFAULT_MODEL, DeepSeekModelOptions.DEFAULT_BASE_URL,
                        DeepSeekModelOptions.DEFAULT_API_PATH, 1048576L, 16384), List.of(DeepSeekModelOptions.DEFAULT_MODEL));
    }
}

