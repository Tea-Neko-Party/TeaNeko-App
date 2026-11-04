package org.zexnocs.teanekoagent.llm.file_config.form;

import java.util.List;
import java.util.Map;

/**
 * 供应商在自己的模型目录贡献公开样板，不读取用户凭据或发起网络请求。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
public interface ILLMConfigurationAdapter {
    /** @return 名称、说明和可编辑样板，不得包含运行凭据 */
    Template describe();

    /**
     * @param id 已注册路由
     * @param model 默认具体模型
     * @param url 适配器固定地址
     * @param path 适配器固定路径
     * @param context 已核实的默认模型上下文容量
     * @param output 可编辑的初始输出额度，不作为业务隐含上限
     * @return 可保存到原 model.yml 的样板
     */
    static Map<String, Object> defaults(String id, String model, String url, String path, long context, int output) {
        return Map.of("id", id, "model", model, "base-url", url, "api-path", path,
                "api-key", "", "context-window-tokens", context, "context-input-ratio", 0.8, "max-tokens", output);
    }

    /**
     * 公开的供应商样板与模型名称提示。
     * @param name 中文显示名
     * @param description 配置说明
     * @param defaults 公开初值，不得包含实际凭据
     * @param models 模型名称建议，仍允许输入自定义名称
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    record Template(String name, String description, Map<String, Object> defaults, List<String> models) {}
}

