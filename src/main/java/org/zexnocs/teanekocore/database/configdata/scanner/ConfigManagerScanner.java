package org.zexnocs.teanekocore.database.configdata.scanner;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigManagerNotFoundException;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 配置管理器扫描器。
 *
 * @see ConfigManager
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 */
@Service
public class ConfigManagerScanner extends AbstractScanner {
    /// logger
    private final ILogger logger;

    /// 规则名称 -> 配置管理器注解
    private final Map<String, ConfigManager> configManagerMap = new ConcurrentHashMap<>();

    /// 命名空间 -> 配置管理器注解列表
    private final Map<String, Set<ConfigManager>> namespaceToConfigManagerList = new ConcurrentHashMap<>();

    /// 包扫描服务
    private final IBeanScanner iBeanScanner;

    @Autowired
    public ConfigManagerScanner(ILogger logger, IBeanScanner iBeanScanner) {
        this.logger = logger;
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 扫描所有带有 ConfigManager 注解的类。
     */
    @Override
    protected synchronized void _scan() {
        configManagerMap.clear();
        namespaceToConfigManagerList.clear();
        // 遍历所有注册的包下的 ConfigManager 类，将其添加到 eventMap 中
        var beanPairs = iBeanScanner.getBeansWithAnnotation(ConfigManager.class);
        for (var beanPair : beanPairs.values()) {
            var managerAnnotation = beanPair.first();
            var bean = beanPair.second();
            var clazz = iBeanScanner.getBeanClass(bean);
            // 如果未开启，则跳过
            if (!managerAnnotation.enabled()) {
                continue;
            }
            var name = managerAnnotation.value();
            if (name == null || name.isBlank()) {
                // 如果 name 为空，则跳过
                logger.errorWithReport("ConfigManagerScanner",
                        "扫描配置管理器失败，类 " + clazz.getName() + " 的 ConfigManager 注解的 value 为空。");
                continue;
            }
            // 将类添加到 eventMap 中
            if(configManagerMap.containsKey(name)) {
                logger.errorWithReport("ConfigManagerScanner",
                        """
                        扫描配置管理器失败：规则名称 "%s" 冲突：
                        类1：%s
                        类2：%s""".formatted(name, configManagerMap.get(name).getClass().getName(), clazz.getName()));
                continue;
            }
            configManagerMap.put(name, managerAnnotation);
            for(var namespace: managerAnnotation.namespaces()) {
                if(namespace == null || namespace.isBlank()) {
                    logger.errorWithReport("ConfigManagerScanner",
                            "扫描配置管理器失败，类 " + clazz.getName() + " 的 ConfigManager 注解的 namespaces 为空。");
                    continue;
                }
                namespaceToConfigManagerList
                        .computeIfAbsent(namespace, k -> new CopyOnWriteArraySet<>())
                        .add(managerAnnotation);
            }
        }
    }

    /**
     * 根据规则名称获取配置管理器注解。
     * @param name 规则名称
     * @return 配置管理器注解，如果不存在则返回 null
     */
    @NonNull
    public ConfigManager getConfigManager(String name) throws ConfigManagerNotFoundException {
        if(name == null || name.isBlank()) {
            throw new ConfigManagerNotFoundException("配置管理器名称不能为空或空字符串。");
        }
        var value = configManagerMap.get(name);
        if (value == null) {
            throw new ConfigManagerNotFoundException("未找到配置管理器：" + name);
        }
        return value;
    }

    /**
     * 根据命名空间获取配置管理器注解列表。
     */
    @NonNull
    public Set<ConfigManager> getConfigManagersByNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("命名空间不能为空或空字符串。");
        }
        return namespaceToConfigManagerList.getOrDefault(namespace, Set.of());
    }

    /**
     * 获取所有配置管理器注解名称
     * @return 所有配置管理器注解名称的集合
     */
    @NonNull
    public Collection<String> getKeySet() {
        return configManagerMap.keySet();
    }
}
