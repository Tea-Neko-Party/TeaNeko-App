package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 配置数据注册服务实现类，负责将配置数据注册到数据库中。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
@RequiredArgsConstructor
public class ConfigDataRegisterService implements IConfigDataRegisterService {
    /// 数据库命名空间，所有配置数据都将存储在这个命名空间下。
    public final static String DATABASE_NAMESPACE = "config";

    /// 日志记录器，用于在注册过程中记录日志。
    private final ILogger logger;

    /**
     * 注册配置数据到数据库中。该方法会根据 ConfigManager 注解中的信息创建一个新的配置实例，并将其存储在数据库中。
     *
     * @param configManager 配置管理器实例，包含了配置数据类和配置名称等信息。
     * @param key 配置数据的唯一标识符，通常是一个字符串，用于在数据库中区分不同的配置数据。
     */
    @Override
    public void registerConfig(@NonNull ConfigManager configManager, String key) {
        final IDatabaseTaskConfig task;
        try {
            task = prepareRegistration(configManager, key);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(getClass().getName(), "无法注册配置管理", e);
            return;
        }
        task.push();
    }

    /**
     * 注销配置数据，从数据库中删除对应的配置实例。该方法会根据 ConfigManager 注解中的信息找到对应的配置数据，并将其从数据库中删除。
     *
     * @param configManager 配置管理器实例，包含了配置数据类和配置名称等信息。
     * @param key 配置数据的唯一标识符，通常是一个字符串，用于在数据库中区分不同的配置数据。
     * @return 是否找到并提交删除；需要等待实际提交结果时使用 unregisterConfigAsync。
     */
    @Override
    public boolean unregisterConfig(@NonNull ConfigManager configManager, String key) {
        final Optional<IDatabaseTaskConfig> task;
        try {
            task = prepareRemoval(configManager, key);
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(getClass().getName(), "无法注销配置管理", e);
            return false;
        }
        task.ifPresent(IDatabaseTaskConfig::push);
        return task.isPresent();
    }

    /**
     * 获取一个 dto 对象
     *
     * @param configManager 配置管理器实例，包含了配置数据类和配置名称等信息。
     * @return 定位到当前配置管理器存储目标的数据对象
     */
    public IEasyDataDto getDto(ConfigManager configManager) {
        return GeneralEasyData.of(ConfigDataRegisterService.DATABASE_NAMESPACE)
                .get(configManager.value());
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
        return prepareRegistration(configManager, key).pushAsync();
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
        return prepareRemoval(configManager, key)
                .map(task -> task.pushAsync().thenApply(ignored -> true))
                .orElseGet(() -> CompletableFuture.completedStage(false));
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private IDatabaseTaskConfig prepareRegistration(ConfigManager configManager, String key) {
        var name = requireName(configManager);
        var configType = configManager.configType();
        if (configType == null) {
            throw new IllegalArgumentException("配置数据类为空");
        }
        final Object config;
        try {
            config = configType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法创建配置实例：" + configType.getName(), e);
        }
        return getDto(configManager).getTaskConfig("注册配置管理: " + name).set(key, config);
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 符合当前定位与类型约束的值；未配置或不存在时返回空
     */
    private Optional<IDatabaseTaskConfig> prepareRemoval(ConfigManager configManager, String key) {
        var name = requireName(configManager);
        var dto = getDto(configManager);
        if (!dto.has(key)) {
            return Optional.empty();
        }
        return Optional.of(dto.getTaskConfig("注销配置管理: " + name).remove(key));
    }

    /**
     * 要求配置管理器具有有效名称，避免以空名称构造存储定位。
     *
     * @param configManager 目标配置管理器声明
     * @return 按本方法契约生成的文本或标识
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private String requireName(ConfigManager configManager) {
        var name = configManager.value();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("配置名称为空");
        }
        return name;
    }
}
