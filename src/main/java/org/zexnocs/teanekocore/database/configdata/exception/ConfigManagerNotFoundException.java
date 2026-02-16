package org.zexnocs.teanekocore.database.configdata.exception;

/**
 * 当在 ConfigManagerScanner 中未找到指定的配置管理器时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ConfigManagerNotFoundException extends RuntimeException {
    public ConfigManagerNotFoundException(String message) {
        super(message);
    }
}
