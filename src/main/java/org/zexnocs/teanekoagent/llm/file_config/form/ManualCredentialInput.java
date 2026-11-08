package org.zexnocs.teanekoagent.llm.file_config.form;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 只保存本次明确输入的新密钥，不回显原密钥。
 *
 * 修改记录：<br>
 * 2026/11/04：业务标识与 Spring Bean 名解耦，使用独立 id 声明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
// 2026/11/04：保持原业务标识，容器按实现类生成 Bean 名。
@LLMCredentialInput(id = "manual")
public final class ManualCredentialInput implements ILLMCredentialInput {
    /** @return 不包含实际秘密的输入规则 */
    @Override
    public Input describe() {
        return new Input("手动填入", "只保存本次明确输入的新密钥，不回显原密钥。", "", "",
                "^[^\\r\\n]{1,8192}$", true, "输入新的 API key");
    }
}

