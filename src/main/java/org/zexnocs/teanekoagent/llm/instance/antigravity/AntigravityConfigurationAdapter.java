package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.zexnocs.teanekoagent.llm.file_config.form.ILLMConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.file_config.form.LLMConfigurationAdapter;

import java.util.List;

/**
 * 通过既有配置目录发布原生代理样板，前端无需增加供应商分支。
 * <br>2026/11/10：连接使用代理根地址及模型集合路径，具体型号在请求时安全追加。
 * <br>2026/11/10：声明随前端交付的本地图标，模型选择沿用通用目录展示。
 * <br>2026/11/10：配置说明标明默认流式接收及完整结果交付。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@LLMConfigurationAdapter(id = AntigravityModel.PROVIDER, icon = "/console/models/antigravity.svg")
public final class AntigravityConfigurationAdapter implements ILLMConfigurationAdapter {
    /** @return 不含运行凭据的可编辑样板，代理别名和真实容量仍须由部署者核对。 */
    @Override
    public Template describe() {
        return new Template("Antigravity（Gemini 原生）",
                "连接 Gemini 原生 API；基础地址不带 /v1。普通集合路径为 /v1beta/models，"
                        + "需要分组专用入口时填写 /antigravity/v1beta/models。默认流式接收后交付完整回答；"
                        + "代理不支持时在模型配置中设置 stream=false。请核对代理模型映射。",
                ILLMConfigurationAdapter.defaults(AntigravityModel.PROVIDER, AntigravityModel.DEFAULT_MODEL,
                        AntigravityModel.DEFAULT_BASE_URL, AntigravityModel.DEFAULT_API_PATH, 1048576L, 16384),
                List.of(AntigravityModel.DEFAULT_MODEL));
    }
}
