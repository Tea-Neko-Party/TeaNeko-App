package org.zexnocs.teanekocore.database.configdata.exception;

/**
 * 当未找到指定的 ConfigData 时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ConfigDataNotFoundException extends RuntimeException {
    public ConfigDataNotFoundException(String message) {
        super(message);
    }
}
