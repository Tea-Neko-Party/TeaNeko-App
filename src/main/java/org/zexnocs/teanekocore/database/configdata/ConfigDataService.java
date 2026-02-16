package org.zexnocs.teanekocore.database.configdata;

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

import java.util.Optional;

/**
 * 配置数据服务，提供对配置数据的注册、获取和设置功能。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ConfigDataService implements IConfigDataService {
    private final IConfigDataRegisterService iConfigDataRegisterService;
    private final IConfigDataGetService iConfigDataGetService;
    private final IConfigDataSetService iConfigDataSetService;

    public ConfigDataService(IConfigDataRegisterService iConfigDataRegisterService,
                             IConfigDataGetService iConfigDataGetService,
                             IConfigDataSetService iConfigDataSetService) {
        this.iConfigDataRegisterService = iConfigDataRegisterService;
        this.iConfigDataGetService = iConfigDataGetService;
        this.iConfigDataSetService = iConfigDataSetService;
    }

    /**
     * 获取配置数据。
     *
     * @param configManager 配置管理器实例，可以是任何带有 @ConfigManager 注解的类的实例。
     * @param configClass 配置数据的类类型，必须实现 IConfigData 接口。
     * @param key 配置数据的唯一标识符，用于区分不同的配置数据。
     * @return {@link Optional }<{@link T }>
     */
    @Override
    public <T extends IConfigData> Optional<T> getConfigData(Object configManager,
                                                             Class<T> configClass,
                                                             String key) {
        return iConfigDataGetService.getConfigData(configManager, configClass, key);
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
     * @return boolean
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
     *
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
}
