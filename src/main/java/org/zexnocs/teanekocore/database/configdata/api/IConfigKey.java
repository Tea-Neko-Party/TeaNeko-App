package org.zexnocs.teanekocore.database.configdata.api;

/**
 * 获取配置键的接口。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 */
@FunctionalInterface
public interface IConfigKey {
    /**
     * 获取配置键。
     * @return 配置键
     */
    String getKey();
}
