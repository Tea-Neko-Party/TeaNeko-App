package org.zexnocs.teanekocore.database.itemdata.exception;

/**
 * 物品数据未找到异常
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ItemDataNotFoundException extends RuntimeException {
    public ItemDataNotFoundException(String message) {
        super(message);
    }
}
