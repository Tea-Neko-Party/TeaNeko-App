package org.zexnocs.teanekoagent.llm.framework.model;

/**
 * 可向配置状态界面报告的错误；消息和异常链不得含配置值或凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public class LLMConfigurationException extends IllegalStateException {
    /**
     * 建立配置边界失败信号；传入说明不得包含密钥、完整地址或其他敏感配置值。
     *
     * @param message 需要发送或映射的消息
     */
    public LLMConfigurationException(String message) {
        super(message);
    }
}
