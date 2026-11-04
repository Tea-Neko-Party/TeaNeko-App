package org.zexnocs.teanekoagent.llm.file_config.form;

/**
 * 用声明式输入规则构造文件值，不向浏览器读取或返回环境变量的实际内容。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
public interface ILLMCredentialInput {
    /** @return 页面输入提示与编码规则，不能包含已配置的秘密 */
    Input describe();

    /**
     * 凭据输入说明，新增方案由原文件或凭据解析器支持其编码结果。
     * @param name 中文名称
     * @param description 用途与约束
     * @param prefix 文件值前缀
     * @param suffix 文件值后缀
     * @param pattern 输入校验表达式
     * @param secret 输入框是否掩码
     * @param placeholder 输入提示
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    record Input(String name, String description, String prefix, String suffix, String pattern,
                 boolean secret, String placeholder) {}
}

