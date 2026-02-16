package org.zexnocs.teanekocore.database.configdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;

/**
 * 配置数据修改服务接口，提供修改配置数据的方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IConfigDataSetService {

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
    default void setRuleConfigField(@NonNull ConfigManager configManager, IConfigKey key, String fieldName, String value)
            throws ConfigDataNotFoundException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        setRuleConfigField(configManager, key.getKey(), fieldName, value);
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
    void setRuleConfigField(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException;

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
    default void addToRuleConfigListFiled(@NonNull ConfigManager configManager, IConfigKey key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        addToRuleConfigListFiled(configManager, key.getKey(), fieldName, value);
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
    void addToRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException;

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
    default void removeFromRuleConfigListFiled(@NonNull ConfigManager configManager, IConfigKey key, String fieldName, int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        removeFromRuleConfigListFiled(configManager, key.getKey(), fieldName, index);
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
    void removeFromRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException;

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
    default void clearRuleConfigListFiled(@NonNull ConfigManager configManager, IConfigKey key, String fieldName)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        clearRuleConfigListFiled(configManager, key.getKey(), fieldName);
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
    void clearRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName)
        throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException;
}
