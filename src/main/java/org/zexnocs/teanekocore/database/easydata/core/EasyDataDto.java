package org.zexnocs.teanekocore.database.easydata.core;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.core.exception.JsonSerializationFailedException;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EasyData 数据传输对象。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@Getter
public class EasyDataDto implements IEasyDataDto {
    /// databaseService
    private final IDatabaseService databaseService;

    /// 数据库类
    private final BaseEasyDataRepository<?> repository;

    /// 任务链命名空间
    private final String taskStageNamespace;

    /// 命名空间
    private final String namespace;

    /// target
    private final String target;

    /// 数据。如果为 null，表示需要从数据库中获取。
    private volatile Map<String, String> data = null;

    /// objectMapper
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     * 不能私自创建 EasyDataDto 对象。
     * @param databaseService 数据库服务。
     * @param taskStageNamespace 任务阶段链命名空间。
     * @param namespace 命名空间。
     * @param target 目标。
     */
    protected EasyDataDto(IDatabaseService databaseService,
                BaseEasyDataRepository<?> repository,
                String taskStageNamespace,
                String namespace,
                String target,
                ObjectMapper objectMapper) {
        this.databaseService = databaseService;
        this.repository = repository;
        this.taskStageNamespace = taskStageNamespace;
        this.namespace = namespace;
        this.target = target;
        this.objectMapper = objectMapper;
    }

    /**
     * 从数据库中加载数据。
     */
    @Override
    public void refresh() {
        // 用临时局部变量构造新数据，避免构建过程中 data 被其他线程访问
        Map<String, String> newData = new ConcurrentHashMap<>();

        // 从数据库加载数据
        var dataList = repository.findByNamespaceAndTarget(namespace, target);
        for (var dto : dataList) {
            newData.put(dto.getKey(), dto.getValue());
        }

        // 原子性地替换 data 引用
        this.data = newData;
    }

    /**
     * 懒加载数据。
     * 如果数据为null，刷新数据。
     */
    protected synchronized void refreshIfNecessary() {
        if (data == null) {
            refresh();
        }
    }

    /**
     * 判断是否存在键。
     * @param key 键。
     * @return 是否存在。
     */
    @Override
    public boolean has(String key) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 判断是否存在键。
        return data.containsKey(key);
    }

    /**
     * 根据键获取字符串值。
     * 如果不存在则返回null。
     * @param key 键。
     * @return 字符串值。
     */
    @Nullable
    @Override
    public String get(String key) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        return data.get(key);
    }

    /**
     * 根据键尝试解析 Json 字符串为指定类型。
     * @param key 键。
     * @param clazz 类型。
     * @param defaultValue 默认值。
     * @return 解析后的值。
     * @param <T> 类型。
     */
    @Nullable
    @Override
    public <T> T get(String key, @NonNull Class<T> clazz, T defaultValue) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 如果键不存在，返回默认值
        if (key == null) {
            return defaultValue;
        }
        // 如果 clazz 为 Void，则返回 null
        if (clazz.equals(Void.class)) {
            return null;
        }
        // 从缓存中获取值
        String value = data.get(key);
        // 如果不存在，返回默认值
        if (value == null) {
            return defaultValue;
        }
        // 如果是String，直接返回
        if (clazz == String.class) {
            return clazz.cast(value);
        }
        // 如果不是String，尝试用Json解析
        try {
            // 如果是 List，则不能直接用 Json 解析
            if(List.class.isAssignableFrom(clazz)) {
                JavaType javaType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class);
                return objectMapper.readValue(value, javaType);
            }
            return objectMapper.readValue(value, clazz);
        } catch (Exception e) {
            throw new JsonSerializationFailedException("""
                    Json 反序列化失败。
                    键：%s
                    要求的类型：%s
                    提供的类型：%s""".formatted(key, clazz.getName(), value.getClass().getName()),
                    e);
        }
    }

    /**
     * 获取所有键。
     * @return 所有键。
     */
    @NonNull
    @Override
    public Collection<String> keySet() {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 返回所有键。
        return data.keySet();
    }

    /**
     * 获取 EasyDataDto 任务配置对象。
     *
     * @param taskName 任务名称。
     * @return EasyDataDto 任务配置对象。
     */
    @Override
    public IEasyDataDtoTaskConfig getTaskConfig(String taskName) {
        return new EasyDataDtoTaskConfig(taskName);
    }

    /**
     * EasyDataDto 任务配置类。
     */
    public class EasyDataDtoTaskConfig extends DatabaseTaskConfig implements IEasyDataDtoTaskConfig {
        /**
         * 构造函数。
         * 不准私自创建。
         * @param taskName        任务名称
         */
        private EasyDataDtoTaskConfig(String taskName) {
            super(databaseService, taskName);
        }

        /**
         * 设置值，并在数据库中进行更新。
         * 如果值为 null，则删除该键。
         * 是异步更新，不是实时的，所以如果有任何 callback 请直接使用 add() 方法添加。
         * @param key 键。
         * @param value 值。
         * @param <T> 类型。
         */
        @Override
        public <T> IEasyDataDtoTaskConfig set(String key, @Nullable T value) {
            // 如果数据为null，刷新数据。
            refreshIfNecessary();
            // 如果值为null，删除键
            if (value == null) {
                return remove(key);
            }
            // 如果值是String，直接存储
            if (value instanceof String str) {
                // 数据库写入
                addTransactionTask(() -> repository.updateDataObjectData(namespace, target, key, str));
                // 缓存写入
                addCacheTask(() -> data.put(key, str));
                return this;
            }
            // 如果不是String，尝试用Json序列化
            try {
                // 序列化
                String str = objectMapper.writeValueAsString(value);
                // 数据库写入
                addTransactionTask(() -> repository.updateDataObjectData(namespace, target, key, str));
                // 缓存写入
                addCacheTask(() -> data.put(key, str));
            } catch (Exception e) {
                throw new JsonSerializationFailedException("""
                        Json 序列化失败。
                        键：%s
                        类型：%s
                        值：%s""".formatted(key, value.getClass().getName(), value.toString()),
                        e);
            }
            return this;
        }

        /**
         * 删除键。
         * 如果直接操作数据库则应该加上事务注解。
         * @param key 键。
         */
        @Override
        public IEasyDataDtoTaskConfig remove(String key) {
            // 如果数据为null，刷新数据。
            refreshIfNecessary();
            // 数据库写入
            addTransactionTask(() -> repository.deleteByNamespaceAndTargetAndKey(namespace, target, key));
            // 缓存写入
            addCacheTask(() -> data.remove(key));
            return this;
        }
    }
}
