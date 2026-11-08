package org.zexnocs.teanekoagent.llm.file_config.form;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;
import java.util.Map;

/**
 * 供应商在自己的模型目录贡献公开样板，不读取用户凭据或发起网络请求。
 *
 * <br>2026/11/04：样板增加各型号上下文容量声明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
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
     * 修改记录：<br>
     * 2026/11/04：增加各型号真实上下文容量声明。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param name 中文显示名
     * @param description 配置说明
     * @param defaults 公开初值，不得包含实际凭据
     * @param models 模型名称建议，仍允许输入自定义名称
     * @param contextWindows 供应商人工维护的各型号真实上下文容量
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.8.0-l
     */
    record Template(String name, String description, Map<String, Object> defaults, List<String> models,
                    Map<String, Long> contextWindows) {
        /**
         * 2026/11/04：同容量型号沿用供应商默认容量，也可另外声明各型号容量。
         * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
         * @param name 显示名
         * @param description 使用说明
         * @param defaults 公开初值
         * @param models 维护的型号列表
         */
        public Template(String name, String description, Map<String, Object> defaults, List<String> models) {
            this(name, description, defaults, models, models.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    java.util.function.Function.identity(), ignored ->
                            ((Number) defaults.get("context-window-tokens")).longValue())));
        }
    }
}

