package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.easydata.core.exception.JsonSerializationFailedException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 数据传输对象接口
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IEasyDataDto {
    /**
     * 取得不使用普通缓存的事务视图，必须在外层数据库任务事务中使用。
     * @return 同域同目标的事务 DTO
     * @throws UnsupportedOperationException 当自定义 DTO 尚未支持事务操作时
     */
    default IEasyDataTransactionDto atomic() { throw new UnsupportedOperationException("当前 EasyData DTO 不支持事务视图。"); }
    /**
     * 获取命名空间。
     * @return 固定业务域
     */
    String getNamespace();

    /**
     * 获取 target。
     * @return 固定目标
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
     * @throws JsonSerializationFailedException 当正文不能解析为目标类型时
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
     * @param <T> 目标类型
     * @return 解析值或默认值
     * @throws JsonSerializationFailedException 当正文不能解析为目标类型时
     */
    @NonNull
    @SuppressWarnings("unchecked")
    default <T> T get(String key, @NonNull T defaultValue) throws JsonSerializationFailedException {
        return Objects.requireNonNull(get(key, (Class<T>) defaultValue.getClass(), defaultValue));
    }

    /**
     * 根据键获取 List 值
     * <br>注意这个 List 是 ImmutableList，不能修改。
     *
     * @param key 键
     * @param clazz List 中元素的类型
     * @param <T> 元素类型
     * @return 反序列化列表，缺失时为空
     * @throws JsonSerializationFailedException 当列表正文无法解析时
     */
    <T> List<T> getList(String key, @NonNull Class<T> clazz) throws JsonSerializationFailedException;

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
     * 获取所有值为 value 的 key。
     *
     * @param value 值。
     * @return 包含所有 key 的集合。
     */
    List<String> getKeysByValue(String value);

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
