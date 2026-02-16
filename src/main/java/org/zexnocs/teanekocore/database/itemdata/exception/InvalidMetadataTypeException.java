package org.zexnocs.teanekocore.database.itemdata.exception;

/**
 * 无效的物品元数据类型异常
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class InvalidMetadataTypeException extends RuntimeException {
    public InvalidMetadataTypeException(String message) {
        super(message);
    }
}
