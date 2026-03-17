package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.List;
import java.util.Optional;

/**
 * 配置数据获取服务实现类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
@RequiredArgsConstructor
public class ConfigDataGetService implements IConfigDataGetService {
    private final ConfigDataRegisterService configDataRegisterService;

    /**
     * 获取配置数据
     *
     * @param configManager 配置管理器注解实例
     * @param configClass 配置数据类
     * @param key 配置数据的键
     * @return 置数据对象的 Optional 包装类，如果未找到或未启用，则返回 Optional.empty()
     * @throws IllegalArgumentException 如果配置管理器未启用，或者配置类与配置管理器不匹配，则抛出该异常
     */
    @Override
    public <T extends IConfigData> Optional<T> getConfigData(ConfigManager configManager,
                                                             Class<T> configClass,
                                                             String key)
            throws IllegalArgumentException {
        // 如果未开启，则返回空
        if (!configManager.enabled()) {
            return Optional.empty();
        }

        var configClazz = configManager.configType();
        if (configClazz == null) {
            throw new IllegalArgumentException("规则注解中的配置类为空。请检查规则实现。");
        }
        // 如果配置类与传入的 configClass 不匹配，则抛出异常
        if (!configClazz.isAssignableFrom(configClass)) {
            throw new IllegalArgumentException("配置类不匹配。请检查规则实现。");
        }
        return Optional.ofNullable(configDataRegisterService.getDto(configManager)
                .get(key, configClass));
    }

    /**
     * 获取配置数据（不指定配置类版本）。
     * 如果配置管理器启用且配置类不为 null，则返回配置数据的 Optional 包装类；否则返回 Optional.empty()。
     *
     * @param configManager 配置管理器注解实例
     * @param key 配置数据的键
     * @return 配置数据对象的 Optional 包装类，如果未找到或未启用，则返回 Optional.empty()
     * @throws IllegalArgumentException 如果配置管理器未启用，或者配置类为 null，则抛出该异常
     */
    @Override
    public Optional<IConfigData> getConfigData(
            @NonNull ConfigManager configManager,
            @NonNull String key) throws IllegalArgumentException {
        // 如果未开启，则返回空
        if (!configManager.enabled()) {
            return Optional.empty();
        }

        var configClazz = configManager.configType();
        if (configClazz == null) {
            throw new IllegalArgumentException("规则注解中的配置类为空。请检查规则实现。");
        }

        return Optional.ofNullable(configDataRegisterService.getDto(configManager)
                .get(key, configClazz));
    }

    /**
     * 获取某一个 ConfigManager 下所有注册的 key
     *
     * @param configManager configManager
     * @return key list
     */
    @Override
    public List<String> getAllConfigKeys(@NonNull ConfigManager configManager) {
        return configDataRegisterService.getDto(configManager).keySet().stream().toList();
    }

}
