package org.zexnocs.teanekocore.database.configdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.Optional;

/**
 * 用于获取配置数据的服务接口。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IConfigDataGetService {
    /**
     * 根据 key 获取对应 manager 的配置数据。
     * @return 配置数据
     * @throws IllegalArgumentException 如果规则注解中的配置类为空或者与 configManager 不匹配
     */
    default <T extends IConfigData> Optional<T> getConfigData(ConfigManager configManager,
                                                              Class<T> configClass,
                                                              IConfigKey key)
            throws IllegalArgumentException {
        return getConfigData(configManager, configClass, key.getKey());
    }

    /**
     * 根据 key 获取对应 manager 的配置数据。
     * @return 配置数据
     * @throws IllegalArgumentException 如果规则注解中的配置类为空
     */
    <T extends IConfigData> Optional<T> getConfigData(ConfigManager configManager,
                                                      Class<T> configClass,
                                                      String key)
            throws IllegalArgumentException;

    /**
     * 根据 key 获取对应 manager 的配置数据。
     * @return 配置数据
     * @throws IllegalArgumentException 如果规则注解中的配置类为空 / 与 configManager 不匹配 / 没有 ConfigManager 注解
     */
    default <T extends IConfigData> Optional<T> getConfigData(@NonNull Object configManagerObject,
                                                              Class<T> configClass,
                                                              IConfigKey key)
            throws IllegalArgumentException {
        return getConfigData(configManagerObject, configClass, key.getKey());
    }

    /**
     * 根据 key 获取对应 manager 的配置数据。
     * @return 配置数据
     * @throws IllegalArgumentException 如果规则注解中的配置类为空 / 与 configManager 不匹配 / 没有 ConfigManager 注解
     */
    default <T extends IConfigData> Optional<T> getConfigData(@NonNull Object configManagerObject,
                                                              Class<T> configClass,
                                                              String key)
            throws IllegalArgumentException {
        // 尝试获取 ConfigManager 注解
        var configManager = configManagerObject.getClass().getAnnotation(ConfigManager.class);
        if (configManager == null) {
            throw new IllegalArgumentException("没有 ConfigManager 注解。请检查规则实现。");
        }
        return getConfigData(configManager, configClass, key);
    }

    /**
     * 获取野生的配置数据。
     *
     * @param configManager 配置管理器注解
     * @param key 配置数据的 key
     * @return {@link Optional }<{@link IConfigData }> 配置数据，如果不存在则返回 {@link Optional#empty()}
     * @throws IllegalArgumentException 如果规则注解中的配置类为空 / 与 configManager 不匹配 / 没有 ConfigManager 注解
     */
    Optional<IConfigData> getConfigData(@NonNull ConfigManager configManager, @NonNull String key)
            throws IllegalArgumentException;
}
