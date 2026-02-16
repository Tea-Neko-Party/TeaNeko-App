package org.zexnocs.teanekocore.database.itemdata.exception;

import org.zexnocs.teanekocore.database.base.exception.DatabaseRetryException;

/**
 * 当物品数据操作因数据完整性违规而失败时抛出此异常，表示可以重试操作。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ItemDataIntegrityViolationRetryException extends DatabaseRetryException {
    public ItemDataIntegrityViolationRetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
