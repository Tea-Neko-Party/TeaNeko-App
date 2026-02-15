package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.easydata.core.exception.JsonSerializationFailedException;

import java.util.Collection;
import java.util.Objects;

/**
 * 数据传输对象接口
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IEasyDataDto {
    /**
     * 获取命名空间。
     */
    String getNamespace();

    /**
     * 获取 target。
     */
    String getTarget();

    /**
     * 从数据库中刷新数据。
     */
    void refresh();

    /**
     * 是否包含某个键。
     * @param key 键。
     * @return 是否包含。
     */
    boolean has(String key);

    /**
     * 根据键获取字符串值。
     * 如果不存在则返回null。
     * @param key 键。
     * @return 字符串值。
     */
    @Nullable
    String get(String key);

    /**
     * 根据键、类型和默认值获取值。
     * 如果不是String，尝试用Json解析。
     * 如果不存在则返回默认值。
     * @param key 键。
     * @param clazz 类型。
     * @param defaultValue 默认值。
     * @return 值。
     * @param <T> 类型。
     * @throws JsonSerializationFailedException 如果Json解析失败则抛出该异常。
     */
    @Nullable
    <T> T get(String key, @NonNull Class<T> clazz, @Nullable T defaultValue) throws JsonSerializationFailedException;

    /**
     * 根据键和类型获取值。
     * 默认值为 null。
     * @param key 键。
     * @param clazz 类型。
     * @return 值。
     * @param <T> 类型。
     */
    @Nullable
    default <T> T get(String key, @NonNull Class<T> clazz) throws JsonSerializationFailedException {
        return get(key, clazz, null);
    }

    /**
     * 根据键和默认值获取值。
     * 默认值不能为null。
     * @param key 键。
     * @param defaultValue 默认值。
     */
    @NonNull
    @SuppressWarnings("unchecked")
    default <T> T get(String key, @NonNull T defaultValue) throws JsonSerializationFailedException {
        return Objects.requireNonNull(get(key, (Class<T>) defaultValue.getClass(), defaultValue));
    }

    /**
     * 根据键获取布尔值。
     * 如果不存在则返回false。
     * @param key 键。
     * @return 布尔值。
     */
    default boolean getBoolean(String key) {
        return Boolean.TRUE.equals(get(key, Boolean.class, false));
    }

    /**
     * 获取 keySet。
     * @return keySet。
     */
    @NonNull
    Collection<String> keySet();

    /**
     * 获取 EasyDataDtoTask 任务。
     * 该任务用于修改 EasyDataDto 数据到数据库。
     * 使用完后，记得使用 .push() 方法将任务交给数据库服务处理。
     * @param taskName 任务名称。
     * @return 任务。
     */
    IEasyDataDtoTaskConfig getTaskConfig(String taskName);
}
