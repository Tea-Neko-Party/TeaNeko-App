package org.zexnocs.teanekocore.database.configdata.exception;

import lombok.Getter;

/**
 * 如果
 * {@link org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker}
 * 检测该域失败则抛出该异常
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@Getter
public class ConfigFieldCheckerFailureException extends RuntimeException {
    /// 要发送的消息文本
    private final String text;

    public ConfigFieldCheckerFailureException(String text) {
        this.text = text;
    }
}
