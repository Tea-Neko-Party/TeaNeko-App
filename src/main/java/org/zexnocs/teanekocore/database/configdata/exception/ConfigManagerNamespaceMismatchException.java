package org.zexnocs.teanekocore.database.configdata.exception;

/**
 * 当 ConfigManager 的命名空间与 ConfigData 的命名空间不匹配时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ConfigManagerNamespaceMismatchException extends RuntimeException {
    public ConfigManagerNamespaceMismatchException(String message) {
        super(message);
    }
}
