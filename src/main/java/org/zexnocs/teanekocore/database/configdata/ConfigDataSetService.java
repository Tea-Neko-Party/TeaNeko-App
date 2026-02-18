package org.zexnocs.teanekocore.database.configdata;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataSetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;
import tools.jackson.databind.ObjectMapper;

/**
 * 配置数据修改服务实现类，提供修改配置数据的方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ConfigDataSetService implements IConfigDataSetService {

    private final IConfigDataGetService iConfigDataGetService;
    private final ILogger logger;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfigDataSetService(IConfigDataGetService iConfigDataGetService,
                                ILogger logger) {
        this.iConfigDataGetService = iConfigDataGetService;
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 使用字段的方法设置群管理规则的配置。
     * @param configManager 配置管理器
     * @param key 规则配置的键
     * @param fieldName  字段名称
     * @param value 字段值
     * @throws ConfigDataNotFoundException 如果未找到规则配置
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalArgumentException 如果字段值不合法
     * @throws IllegalAccessException 如果无法访问字段
     */
    @Override
    public void setRuleConfigField(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException {
        Object config;
        try {
            config = iConfigDataGetService
                    .getConfigData(configManager, key)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取群管理规则配置失败", e);
            return;
        }
        if(config == null) {
            throw new ConfigDataNotFoundException("配置数据未找到: " + key);
        }

        // 更新数据库
        var name = configManager.value();
        var task = GeneralEasyData.of(ConfigDataRegisterService.DATABASE_NAMESPACE)
                .get(key)
                .getTaskConfig("更新群管理规则配置: " + name);
        task.set(name, ObjectFieldUtil.Instance.setFieldValue(objectMapper, config, fieldName, value));
        task.push();
    }

    /**
     * 为 Config 中 List 的字段添加一个值。
     * @param configManager 配置管理器
     * @param key 规则配置的键
     * @param fieldName 字段名称
     * @param value 字段值
     * @throws ConfigDataNotFoundException 如果未找到规则配置
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalArgumentException 如果字段值不合法
     * @throws IllegalAccessException 如果无法访问字段
     * @throws ObjectFieldUtil.FieldNotListException 如果指定字段不是 List 类型
     */
    @Override
    public void addToRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        Object config;
        try {
            config = iConfigDataGetService
                    .getConfigData(configManager, key)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取群管理规则配置失败", e);
            return;
        }
        if(config == null) {
            throw new ConfigDataNotFoundException("配置数据未找到: " + key);
        }

        // 更新数据库
        var name = configManager.value();
        var task = GeneralEasyData.of(ConfigDataRegisterService.DATABASE_NAMESPACE)
                .get(key)
                .getTaskConfig("更新群管理规则配置: " + name);
        task.set(name, ObjectFieldUtil.Instance.addToListField(objectMapper, config, fieldName, value));
        task.push();
    }

    /**
     * 为 Config 中 List 的字段移除一个值。
     * @param configManager 配置管理器
     * @param key 规则配置的键
     * @param fieldName 字段名称
     * @param index 索引
     * @throws ConfigDataNotFoundException 如果未找到规则配置
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalAccessException 如果无法访问字段
     * @throws IndexOutOfBoundsException 如果索引超出范围
     * @throws ObjectFieldUtil.FieldNotListException 如果指定字段不是 List 类型
     */
    @Override
    public void removeFromRuleConfigListFiled(@NonNull ConfigManager configManager,
                                              String key,
                                              String fieldName,
                                              int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        Object config;
        try {
            config = iConfigDataGetService
                    .getConfigData(configManager, key)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取群管理规则配置失败", e);
            return;
        }
        if(config == null) {
            throw new ConfigDataNotFoundException("配置数据未找到: " + key);
        }

        // 更新数据库
        var name = configManager.value();
        var task = GeneralEasyData.of(ConfigDataRegisterService.DATABASE_NAMESPACE)
                .get(key)
                .getTaskConfig("更新群管理规则配置: " + name);
        task.set(name, ObjectFieldUtil.Instance.removeFromListField(config, fieldName, index));
        task.push();
    }

    /**
     * 清理一个群管理规则的配置。
     * @param configManager 配置管理器
     * @param key 规则配置的键
     * @param fieldName 字段名称
     * @throws ConfigDataNotFoundException 如果未找到规则配置
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalAccessException 如果无法访问字段
     * @throws ObjectFieldUtil.FieldNotListException 如果指定字段不是 List 类型
     */
    @Override
    public void clearRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        Object config;
        try {
            config = iConfigDataGetService
                    .getConfigData(configManager, key)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取群管理规则配置失败", e);
            return;
        }
        if(config == null) {
            throw new ConfigDataNotFoundException("配置数据未找到: " + key);
        }

        // 更新数据库
        var name = configManager.value();
        var task = GeneralEasyData.of(ConfigDataRegisterService.DATABASE_NAMESPACE)
                .get(key)
                .getTaskConfig("更新群管理规则配置: " + name);
        task.set(name, ObjectFieldUtil.Instance.clearListField(config, fieldName));
        task.push();
    }
}
