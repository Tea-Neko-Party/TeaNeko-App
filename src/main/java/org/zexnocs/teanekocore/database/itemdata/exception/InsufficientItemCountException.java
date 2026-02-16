package org.zexnocs.teanekocore.database.itemdata.exception;

/**
 * 当物品数量不足时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class InsufficientItemCountException extends RuntimeException {
    /**
     * 构造函数。
     * @param message 异常消息。
     */
    public InsufficientItemCountException(String message) {
        super(message);
    }
}
