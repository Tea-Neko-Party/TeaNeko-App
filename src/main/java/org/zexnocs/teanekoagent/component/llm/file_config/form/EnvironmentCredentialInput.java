package org.zexnocs.teanekoagent.component.llm.file_config.form;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 保存环境占位符，由后端统一解析；这里只填写变量名。
 *
 * 修改记录：<br>
 * 2026/11/04：业务标识与 Spring Bean 名解耦，使用独立 id 声明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.0-l
 */
// 2026/11/04：保持原业务标识，容器按实现类生成 Bean 名。
@LLMCredentialInput(id = "environment")
public final class EnvironmentCredentialInput implements ILLMCredentialInput {
    /** @return 不包含实际秘密的输入规则 */
    @Override
    public Input describe() {
        return new Input("环境变量", "保存环境占位符，由后端统一解析；这里只填写变量名。", "${", "}",
                "^[A-Za-z_][A-Za-z0-9_]*$", false, "例如 DEEPSEEK_API_KEY");
    }
}

