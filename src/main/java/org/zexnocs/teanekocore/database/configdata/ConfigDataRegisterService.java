package org.zexnocs.teanekocore.database.configdata;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 配置数据注册服务实现类，负责将配置数据注册到数据库中。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ConfigDataRegisterService implements IConfigDataRegisterService {
    /// 数据库命名空间，所有配置数据都将存储在这个命名空间下。
    public final static String DATABASE_NAMESPACE = "group_manager";

    /// 日志记录器，用于在注册过程中记录日志。
    private final ILogger logger;

    /**
     * 构造函数，注入 ILogger 以便在注册过程中记录日志。
     *
     * @param generalLogger ILogger 实例，用于记录日志。
     */
    public ConfigDataRegisterService(ILogger generalLogger) {
        this.logger = generalLogger;
    }

    /**
     * 注册配置数据到数据库中。该方法会根据 ConfigManager 注解中的信息创建一个新的配置实例，并将其存储在数据库中。
     *
     * @param configManager 配置管理器实例，包含了配置数据类和配置名称等信息。
     * @param key 配置数据的唯一标识符，通常是一个字符串，用于在数据库中区分不同的配置数据。
     */
    @Override
    public void registerConfig(@NonNull ConfigManager configManager, String key) {
        var configDataClass = configManager.configType();
        if (configDataClass == null) {
            logger.errorWithReport(ConfigDataRegisterService.class.getName(),
                    "无法注册配置管理，因为配置数据类为 null。请检查配置实现。");
            return;
        }
        Object configData;
        try {
            configData = configDataClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.errorWithReport(ConfigDataRegisterService.class.getName(),
                    "无法创建配置管理配置实例。请检查配置数据类是否有无参构造器：" + configDataClass.getName(),
                    e);
            return;
        }
        var name = configManager.value();
        if(name == null || name.isBlank()) {
            logger.errorWithReport(ConfigDataRegisterService.class.getName(),
                    "无法注册配置管理，因为配置名称为 null 或空字符串。请检查配置实现。");
            return;
        }
        // 写入数据库
        var task = GeneralEasyData.of(DATABASE_NAMESPACE)
                .get(key)
                .getTaskConfig("注册配置管理: " + name);
        task.set(name, configData);
        task.push();
    }

    /**
     * 注销配置数据，从数据库中删除对应的配置实例。该方法会根据 ConfigManager 注解中的信息找到对应的配置数据，并将其从数据库中删除。
     *
     * @param configManager 配置管理器实例，包含了配置数据类和配置名称等信息。
     * @param key 配置数据的唯一标识符，通常是一个字符串，用于在数据库中区分不同的配置数据。
     * @return boolean 表示注销是否成功，如果配置数据存在并成功删除则返回 true，否则返回 false。
     */
    @Override
    public boolean unregisterConfig(@NonNull ConfigManager configManager, String key) {
        var name = configManager.value();
        if (name == null || name.isBlank()) {
            logger.errorWithReport(ConfigDataRegisterService.class.getName(),
                    "无法注销配置管理，因为配置名称为 null 或空字符串。请检查配置实现。");
            return false;
        }
        // 从数据库中删除
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE)
                .get(String.valueOf(key));
        if(dto.has(configManager.value())) {
            var task = dto.getTaskConfig("注销配置管理: " + name);
            task.remove(name);
            task.push();
            return true;
        } else {
            // 如果配置不存在，则不进行任何操作
            return false;
        }
    }
}
