package org.zexnocs.teanekocore.database.configdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * 配置数据注册服务接口，用于注册和注销配置数据。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IConfigDataRegisterService {
    /**
     * 使用默认配置注册 configData。
     * @param configManager 配置管理器
     * @param key            配置键
     */
    default void registerConfig(@NonNull ConfigManager configManager, IConfigKey key) {
        registerConfig(configManager, key.getKey());
    }

    /**
     * 使用默认配置注册 configData。
     * @param configManager 配置管理器
     * @param key            配置键
     */
    void registerConfig(@NonNull ConfigManager configManager, String key);

    /**
     * 注销 configData。
     * @param configManager 配置管理器
     * @param key            配置键
     * @return 如果注销成功，则返回 true; 否则返回 false。
     */
    default boolean unregisterConfig(@NonNull ConfigManager configManager, IConfigKey key) {
        return unregisterConfig(configManager, key.getKey());
    }

    /**
     * 注销 configData。
     * @param configManager 配置管理器
     * @param key            配置键
     * @return 如果注销成功，则返回 true; 否则返回 false。
     */
    boolean unregisterConfig(@NonNull ConfigManager configManager, String key);
}
