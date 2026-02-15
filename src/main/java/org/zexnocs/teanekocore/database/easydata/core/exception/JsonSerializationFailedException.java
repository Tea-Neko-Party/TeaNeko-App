package org.zexnocs.teanekocore.database.easydata.core.exception;

/**
 * 当 JSON 序列化失败时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public class JsonSerializationFailedException extends RuntimeException {
    /**
     * 构造一个新的 JsonSerializationFailedException。
     *
     * @param message 异常消息
     * @param cause 引起此异常的原因
     */
    public JsonSerializationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
