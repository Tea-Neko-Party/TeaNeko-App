package org.zexnocs.teanekocore.database.configdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.concurrent.CompletionStage;

/**
 * 配置数据注册服务接口，用于注册和注销配置数据。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IConfigDataRegisterService {
    /**
     * 注册并等待持久化完成；配置声明非法会同步抛出异常。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    CompletionStage<Void> registerConfigAsync(@NonNull ConfigManager configManager, String key);

    /**
     * 注册或重建配置，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    default CompletionStage<Void> registerConfigAsync(@NonNull ConfigManager configManager, IConfigKey key) {
        return registerConfigAsync(configManager, key.getKey());
    }

    /**
     * 存在时等待删除提交后返回 true，不存在时返回已完成的 false。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 删除提交成功后为 true、原配置不存在时为 false 的阶段；提交失败异常完成
     */
    CompletionStage<Boolean> unregisterConfigAsync(@NonNull ConfigManager configManager, String key);

    /**
     * 注销已存在的配置，返回阶段等待实际数据库提交与缓存处理；失败继续传播给调用者。
     *
     * @param configManager 目标配置管理器声明
     * @param key 定位目标数据的键
     * @return 删除提交成功后为 true、原配置不存在时为 false 的阶段；提交失败异常完成
     */
    default CompletionStage<Boolean> unregisterConfigAsync(@NonNull ConfigManager configManager, IConfigKey key) {
        return unregisterConfigAsync(configManager, key.getKey());
    }

    /**
     * 使用默认配置注册 configData。
     *
     * @param configManager 配置管理器
     * @param key            配置键
     */
    default void registerConfig(@NonNull ConfigManager configManager, IConfigKey key) {
        registerConfig(configManager, key.getKey());
    }

    /**
     * 使用默认配置注册 configData。
     *
     * @param configManager 配置管理器
     * @param key            配置键
     */
    void registerConfig(@NonNull ConfigManager configManager, String key);

    /**
     * 注销 configData。
     *
     * @param configManager 配置管理器
     * @param key            配置键
     * @return 找到并提交删除时返回 true，否则返回 false；需要等待完成时使用 unregisterConfigAsync。
     */
    default boolean unregisterConfig(@NonNull ConfigManager configManager, IConfigKey key) {
        return unregisterConfig(configManager, key.getKey());
    }

    /**
     * 注销 configData。
     *
     * @param configManager 配置管理器
     * @param key            配置键
     * @return 找到并提交删除时返回 true，否则返回 false；需要等待完成时使用 unregisterConfigAsync。
     */
    boolean unregisterConfig(@NonNull ConfigManager configManager, String key);
}
