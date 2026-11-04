package org.zexnocs.teanekoagent.component.llm.instance.kimi;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.file_config.form.ILLMConfigurationAdapter;
import org.zexnocs.teanekoagent.component.llm.file_config.form.LLMConfigurationAdapter;
import java.util.List;
import java.util.Map;

/**
 * Kimi 的配置样板，与供应商适配器放在同一目录并复用固定连接常量。
 *
 * 修改记录：<br>
 * 2026/11/04：业务标识与 Spring Bean 名解耦，使用独立 id 声明。
 *
 * <br>2026/11/04：人工维护官方型号及上下文容量。
 *
 * <br>2026/11/04：声明 Kimi 官方图标供模型卡片与新增入口使用。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.0-l
 */
// 2026/11/04：保持原业务标识，容器按实现类生成 Bean 名。
@LLMConfigurationAdapter(id = KimiChatModel.PROVIDER, icon = "/console/models/kimi.png")
public final class KimiConfigurationAdapter implements ILLMConfigurationAdapter {
    /** @return 默认模型的公开样板，模型、输出及上下文容量均可在页面修改 */
    @Override
    public Template describe() {
        return new Template("Kimi", "复用适配器默认连接；更换具体模型或自定义服务后请核对上下文容量。",
                ILLMConfigurationAdapter.defaults(KimiChatModel.PROVIDER, KimiModelOptions.DEFAULT_MODEL, KimiModelOptions.DEFAULT_BASE_URL,
                        // 2026/11/04：人工维护官方型号和容量，具体账号资格仍由供应商校验。
                        KimiModelOptions.DEFAULT_API_PATH, 262144L, 16384),
                List.of("kimi-k3.1-pro", "kimi-k3.1-pro-highspeed", "kimi-k3", "kimi-k2.7-code",
                        "kimi-k2.7-code-highspeed", "kimi-k2.6"),
                Map.of("kimi-k3.1-pro", 1048576L, "kimi-k3.1-pro-highspeed", 1048576L, "kimi-k3", 1048576L,
                        "kimi-k2.7-code", 262144L, "kimi-k2.7-code-highspeed", 262144L, "kimi-k2.6", 262144L));
    }
}

