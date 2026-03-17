package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigFieldCheckerFailureException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataSetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;
import tools.jackson.databind.ObjectMapper;

/**
 * 配置数据修改服务实现类，提供修改配置数据的方法。
 * <br>4.3.4: 支持 checker 检测域设置，并自定义失败语言。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 * @version 4.3.4
 */
@Service
@RequiredArgsConstructor
public class ConfigDataSetService implements IConfigDataSetService {

    private final IConfigDataGetService iConfigDataGetService;
    private final ILogger logger;
    private final ObjectMapper objectMapper;
    private final IConfigFieldCheckerScanner iConfigFieldCheckerScanner;
    private final ConfigDataRegisterService configDataRegisterService;

    /**
     * 使用字段的方法设置群管理规则的配置。
     *
     * @param configManager 配置管理器
     * @param key 规则配置的键
     * @param fieldName  字段名称
     * @param value 字段值
     * @throws ConfigDataNotFoundException 如果未找到规则配置
     * @throws NoSuchFieldException 如果未找到指定字段
     * @throws IllegalArgumentException 如果字段值不合法
     * @throws IllegalAccessException 如果无法访问字段
     * @throws ConfigFieldCheckerFailureException 如果字段值未通过 checker 检测
     */
    @Override
    public void setRuleConfigField(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ConfigFieldCheckerFailureException {
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
        // 获取 checker 检测设置的值是否准确
        checkField(configManager, fieldName, value);

        // 更新数据库
        configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.setFieldValue(objectMapper, config, fieldName, value))
                .push();
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
     * @throws ConfigFieldCheckerFailureException 如果字段值未通过 checker 检测
     */
    @Override
    public void addToRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException,
            ConfigFieldCheckerFailureException {
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

        // 获取 checker 检测设置的值是否准确
        checkField(configManager, fieldName, value);

        // 更新数据库
        configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.addToListField(objectMapper, config, fieldName, value))
                .push();
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
        configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.removeFromListField(config, fieldName, index))
                .push();
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
        configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.clearListField(config, fieldName))
                .push();
    }

    /**
     * 检测 filed 设置设置是否合理
     *
     * @param configManager 配置管理器
     * @param fieldName     域名
     * @param value         尝试修改的值
     */
    private void checkField(@NonNull ConfigManager configManager, String fieldName, String value)
            throws ConfigFieldCheckerFailureException {
        final IConfigFieldChecker checker;
        try {
            checker = iConfigFieldCheckerScanner.get(configManager.fieldChecker());
        } catch (RuntimeException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取 filed checker 失败，没有注册为 bean 或者 不存在合理的无参构造器", e);
            return;
        }

        if(checker != null) {
            var str = checker.isValid(fieldName, value);
            // 检测失败，抛出
            if(str != null) {
                throw new ConfigFieldCheckerFailureException(str);
            }
        }
    }
}
