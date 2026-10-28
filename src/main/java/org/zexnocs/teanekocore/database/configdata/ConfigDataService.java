package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataSetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 配置数据服务，提供对配置数据的注册、获取和设置功能。
 * <br>4.3.4: 支持 filed checker
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
@RequiredArgsConstructor
public class ConfigDataService implements IConfigDataService {
    private final IConfigDataRegisterService iConfigDataRegisterService;
    private final IConfigDataGetService iConfigDataGetService;
    private final IConfigDataSetService iConfigDataSetService;

    /**
     * 获取配置数据。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param configClass 配置数据的类类型，必须实现 IConfigData 接口。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @param <T> 本方法约定的泛型数据类型
     * @return 目标配置数据，不存在时返回空
     */
    @Override
    public <T extends IConfigData> Optional<T> getConfigData(Object configManager,
                                                             Class<T> configClass,
                                                             String key) {
        return iConfigDataGetService.getConfigData(configManager, configClass, key);
    }

    /**
     * 获取注册该 config 的所有 key
     *
     * @param configManager 目标配置管理器声明
     * @return 当前管理器已登记的配置键集合
     */
    @Override
    public List<String> getAllConfigKeys(ConfigManager configManager) {
        return iConfigDataGetService.getAllConfigKeys(configManager);
    }

    /**
     * 注册配置数据。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     */
    @Override
    public void registerConfig(@NonNull ConfigManager configManager, String key) {
        iConfigDataRegisterService.registerConfig(configManager, key);
    }

    /**
     * 注销配置数据。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @return 找到配置并提交注销时为 true；该便捷返回值不表示事务已经完成
     */
    @Override
    public boolean unregisterConfig(@NonNull ConfigManager configManager, String key) {
        return iConfigDataRegisterService.unregisterConfig(configManager, key);
    }

    /**
     * 设置配置数据字段的值。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @param fieldName 要设置的字段名称，必须是配置数据类中的一个字段。
     * @param value 要设置的字段值，必须与字段的类型兼容。
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     * @throws NoSuchFieldException 当指定的字段名称在配置数据类中不存在时抛出此异常。
     * @throws IllegalArgumentException 当提供的值与字段类型不兼容时抛出此异常。
     * @throws IllegalAccessException 当无法访问指定的字段时抛出此异常。
     */
    @Override
    public void setRuleConfigField(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException {
        iConfigDataSetService.setRuleConfigField(configManager, key, fieldName, value);
    }

    /**
     * 向配置数据的List类型字段添加一个值。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @param fieldName 要设置的字段名称，必须是配置数据类中的一个字段，并且该字段必须是一个List类型。
     * @param value 要添加到List字段中的值，必须与List中元素的类型兼容。
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     * @throws NoSuchFieldException 当指定的字段名称在配置数据类中不存在时抛出此异常。
     * @throws IllegalArgumentException 当提供的值与List中元素的类型不兼容时抛出此异常。
     * @throws IllegalAccessException 当无法访问指定的字段时抛出此异常。
     * @throws ObjectFieldUtil.FieldNotListException  表示字段不是List类型的异常。
     */
    @Override
    public void addToRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        iConfigDataSetService.addToRuleConfigListFiled(configManager, key, fieldName, value);
    }

    /**
     * 经配置写服务移除指定列表项，保留既有便捷提交入口语义。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @param fieldName 要设置的字段名称，必须是配置数据类中的一个字段，并且该字段必须是一个List类型。
     * @param index 要从List字段中移除的值的索引，必须是一个有效的索引，且在List的范围内。
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     * @throws NoSuchFieldException 当指定的字段名称在配置数据类中不存在时抛出此异常。
     * @throws IllegalAccessException 当无法访问指定的字段时抛出此异常。
     * @throws IndexOutOfBoundsException 当提供的索引无效时抛出此异常。
     * @throws ObjectFieldUtil.FieldNotListException 表示字段不是List类型的异常。
     */
    @Override
    public void removeFromRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        iConfigDataSetService.removeFromRuleConfigListFiled(configManager, key, fieldName, index);
    }

    /**
     * 清空配置数据的List类型字段中的所有值。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @param fieldName 要设置的字段名称，必须是配置数据类中的一个字段，并且该字段必须是一个List类型。
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     * @throws NoSuchFieldException 当指定的字段名称在配置数据类中不存在时抛出此异常。
     * @throws IllegalAccessException 当无法访问指定的字段时抛出此异常。
     * @throws ObjectFieldUtil.FieldNotListException       表示字段不是List类型的异常。
     */
    @Override
    public void clearRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        iConfigDataSetService.clearRuleConfigListFiled(configManager, key, fieldName);
    }

    /**
     * 注册或重建配置，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    @Override
    public CompletionStage<Void> registerConfigAsync(@NonNull ConfigManager configManager, String key) {
        return iConfigDataRegisterService.registerConfigAsync(configManager, key);
    }

    /**
     * 注销已存在的配置，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 删除提交成功后为 true、原配置不存在时为 false 的阶段；提交失败异常完成
     */
    @Override
    public CompletionStage<Boolean> unregisterConfigAsync(@NonNull ConfigManager configManager, String key) {
        return iConfigDataRegisterService.unregisterConfigAsync(configManager, key);
    }

    /**
     * 设置配置字段，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param value 待处理的值
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> setRuleConfigFieldAsync(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException {
        return iConfigDataSetService.setRuleConfigFieldAsync(configManager, key, fieldName, value);
    }

    /**
     * 向配置列表追加项，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param value 待处理的值
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> addToRuleConfigListFiledAsync(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        return iConfigDataSetService.addToRuleConfigListFiledAsync(configManager, key, fieldName, value);
    }

    /**
     * 按索引移除配置列表项，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param index 目标项索引或展示页码，按当前入口约定解释
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws IndexOutOfBoundsException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> removeFromRuleConfigListFiledAsync(@NonNull ConfigManager configManager, String key, String fieldName, int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        return iConfigDataSetService.removeFromRuleConfigListFiledAsync(configManager, key, fieldName, index);
    }

    /**
     * 清空配置列表，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> clearRuleConfigListFiledAsync(@NonNull ConfigManager configManager, String key, String fieldName)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        return iConfigDataSetService.clearRuleConfigListFiledAsync(configManager, key, fieldName);
    }
}
