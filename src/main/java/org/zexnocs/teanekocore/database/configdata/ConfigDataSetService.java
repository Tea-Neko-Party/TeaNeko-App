package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigFieldCheckerFailureException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataSetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletionStage;

/**
 * 配置数据修改服务实现类，提供修改配置数据的方法。
 * <br>4.3.4: 支持 checker 检测域设置，并自定义失败语言。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
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
        prepareSetRuleConfigField(configManager, key, fieldName, value).push();
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
     * @throws ConfigFieldCheckerFailureException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> setRuleConfigFieldAsync(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ConfigFieldCheckerFailureException {
        return prepareSetRuleConfigField(configManager, key, fieldName, value).pushAsync();
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param value 待处理的值
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws ConfigFieldCheckerFailureException 当该操作的校验或下游调用失败时
     */
    private IDatabaseTaskConfig prepareSetRuleConfigField(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ConfigFieldCheckerFailureException {
        var config = requireConfig(configManager, key);
        // 获取 checker 检测设置的值是否准确
        checkField(configManager, fieldName, value);

        // 更新数据库
        return configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.setFieldValue(objectMapper, config, fieldName, value));
    }

    /**
     * 为 Config 中 List 的字段添加一个值。
     *
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
        prepareAddToRuleConfigListFiled(configManager, key, fieldName, value).push();
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
     * @throws ConfigFieldCheckerFailureException 当该操作的校验或下游调用失败时
     */
    @Override
    public CompletionStage<Void> addToRuleConfigListFiledAsync(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException,
            ConfigFieldCheckerFailureException {
        return prepareAddToRuleConfigListFiled(configManager, key, fieldName, value).pushAsync();
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param value 待处理的值
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     * @throws ConfigFieldCheckerFailureException 当该操作的校验或下游调用失败时
     */
    private IDatabaseTaskConfig prepareAddToRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName, String value)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalArgumentException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException,
            ConfigFieldCheckerFailureException {
        var config = requireConfig(configManager, key);

        // 获取 checker 检测设置的值是否准确
        checkField(configManager, fieldName, value);

        // 更新数据库
        return configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.addToListField(objectMapper, config, fieldName, value));
    }

    /**
     * 为 Config 中 List 的字段移除一个值。
     *
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
        prepareRemoveFromRuleConfigListFiled(configManager, key, fieldName, index).push();
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
    public CompletionStage<Void> removeFromRuleConfigListFiledAsync(@NonNull ConfigManager configManager,
                                              String key,
                                              String fieldName,
                                              int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        return prepareRemoveFromRuleConfigListFiled(configManager, key, fieldName, index).pushAsync();
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @param index 目标项索引或展示页码，按当前入口约定解释
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws IndexOutOfBoundsException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     */
    private IDatabaseTaskConfig prepareRemoveFromRuleConfigListFiled(@NonNull ConfigManager configManager,
                                              String key,
                                              String fieldName,
                                              int index)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            IndexOutOfBoundsException,
            ObjectFieldUtil.FieldNotListException {
        var config = requireConfig(configManager, key);

        // 更新数据库
        return configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.removeFromListField(config, fieldName, index));
    }

    /**
     * 清理一个群管理规则的配置。
     *
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
        prepareClearRuleConfigListFiled(configManager, key, fieldName).push();
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
        return prepareClearRuleConfigListFiled(configManager, key, fieldName).pushAsync();
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @param fieldName 需要操作的配置字段名称
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     * @throws NoSuchFieldException 当该操作的校验或下游调用失败时
     * @throws IllegalAccessException 当该操作的校验或下游调用失败时
     * @throws ObjectFieldUtil.FieldNotListException 当该操作的校验或下游调用失败时
     */
    private IDatabaseTaskConfig prepareClearRuleConfigListFiled(@NonNull ConfigManager configManager, String key, String fieldName)
            throws ConfigDataNotFoundException,
            NoSuchFieldException,
            IllegalAccessException,
            ObjectFieldUtil.FieldNotListException {
        var config = requireConfig(configManager, key);

        // 更新数据库
        return configDataRegisterService.getDto(configManager)
                .getTaskConfig("更新群管理规则配置: " + configManager.value())
                .set(key, ObjectFieldUtil.Instance.clearListField(config, fieldName));
    }

    /**
     * 获取待修改的配置；读取失败或配置缺失均阻止后续提交。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 纯文本内容或保持原图文顺序的协议数组，取决于输入是否包含图片
     * @throws ConfigDataNotFoundException 当该操作的校验或下游调用失败时
     */
    private Object requireConfig(ConfigManager configManager, String key) throws ConfigDataNotFoundException {
        try {
            return iConfigDataGetService.getConfigData(configManager, key)
                    .orElseThrow(() -> new ConfigDataNotFoundException("配置数据未找到: " + key));
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(getClass().getName(), "获取群管理规则配置失败", e);
            throw e;
        }
    }

    /**
     * 校验字段值；检查器不可用时不能跳过校验。
     *
     * @param configManager 目标配置管理器声明
     * @param fieldName 需要操作的配置字段名称
     * @param value 待处理的值
     * @throws ConfigFieldCheckerFailureException 当该操作的校验或下游调用失败时
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    private void checkField(@NonNull ConfigManager configManager, String fieldName, String value)
            throws ConfigFieldCheckerFailureException {
        final IConfigFieldChecker checker;
        try {
            checker = iConfigFieldCheckerScanner.get(configManager.fieldChecker());
        } catch (RuntimeException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取 filed checker 失败，没有注册为 bean 或者 不存在合理的无参构造器", e);
            throw new IllegalStateException("无法加载配置字段检查器", e);
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
