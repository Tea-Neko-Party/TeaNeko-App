package org.zexnocs.teanekocore.database.easydata.core;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.IBaseEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.core.exception.JsonSerializationFailedException;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EasyData 数据传输对象。
 * <br> 4.4.0: 加锁
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
public class EasyDataDto implements IEasyDataDto {
    /// databaseService
    private final IDatabaseService databaseService;

    /// 数据库类
    @Getter(lombok.AccessLevel.NONE)
    private final IBaseEasyDataRepository<?> repository;

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

    /// 锁，防止多线程同时刷新数据导致重复刷新和数据不一致
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 构造函数。
     * 不能私自创建 EasyDataDto 对象。
     * @param databaseService 数据库服务。
     * @param repository 由入口固定的底层仓库。
     * @param taskStageNamespace 任务阶段链命名空间。
     * @param namespace 命名空间。
     * @param target 目标。
     * @param objectMapper JSON 转换器。
     */
    protected EasyDataDto(IDatabaseService databaseService,
                IBaseEasyDataRepository<?> repository,
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
     * 取得当前目标的事务视图，不借助普通 DTO 缓存读取行锁或版本。
     * @return 仅在外层数据库事务中有效的视图
     */
    @Override public IEasyDataTransactionDto atomic() {
        return new EasyDataTransactionDto<>(repository, namespace, target, this::invalidate);
    }
    /** 在已提交写入后惰性失效缓存，不把事务候选值提前发布。 */
    private void invalidate() {
        lock.lock();
        try { data = null; } finally { lock.unlock(); }
    }

    /**
     * 从数据库中加载数据。
     */
    @Override
    public void refresh() {
        lock.lock();
        try {
            // 用临时局部变量构造新数据，避免构建过程中 data 被其他线程访问
            Map<String, String> newData = new ConcurrentHashMap<>();
            // 从数据库加载数据
            var dataList = repository.findByNamespaceAndTarget(namespace, target);
            for (var dto : dataList) {
                newData.put(dto.getKey(), dto.getValue());
            }
            // 原子性地替换 data 引用
            this.data = newData;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 懒加载数据。
     * 如果数据为null，刷新数据。
     */
    protected void refreshIfNecessary() {
        lock.lock();
        try {
            if (data == null) {
                refresh();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在同一锁内取得已加载快照，避免提交失效发生在检查与读取之间。
     * 返回的旧快照允许正在进行的读取完成，后续读取会重新加载。
     * @return 当前完整缓存快照
     */
    private Map<String, String> snapshot() {
        lock.lock();
        try { refreshIfNecessary(); return Objects.requireNonNull(data); }
        finally { lock.unlock(); }
    }

    /**
     * 判断是否存在键。
     *
     * @param key 键。
     * @return 是否存在。
     */
    @Override
    public boolean has(String key) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 判断是否存在键。
        return snapshot().containsKey(key);
    }

    /**
     * 根据键获取字符串值。
     * 如果不存在则返回null。
     *
     * @param key 键。
     * @return 字符串值。
     */
    @Nullable
    @Override
    public String get(String key) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        return snapshot().get(key);
    }

    /**
     * 根据键尝试解析 Json 字符串为指定类型。
     *
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
        String value = snapshot().get(key);
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
     * 根据键获取 List 值
     *
     * @param key   键
     * @param clazz List 中元素的类型
     * @param <T> 元素类型
     * @return 反序列化列表，缺失时为空
     * @throws JsonSerializationFailedException 当列表正文无法解析时
     */
    @Override
    public <T> List<T> getList(String key, @NonNull Class<T> clazz) throws JsonSerializationFailedException {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 从缓存中获取值
        String value = snapshot().get(key);
        // 如果不存在，返回空列表
        if (value == null) {
            return List.of();
        }
        // 尝试用Json解析
        try {
            return objectMapper.readerForListOf(clazz).readValue(value);
        } catch (Exception e) {
            throw new JsonSerializationFailedException("""
                    Json 反序列化失败。
                    键：%s
                    要求的类型：List<%s>
                    提供的类型：%s""".formatted(key, clazz.getName(), value.getClass().getName()),
                    e);
        }
    }

    /**
     * 获取所有值为 value 的 key。
     *
     * @param value 值。
     * @return 包含所有 key 的集合。
     */
    @Override
    public List<String> getKeysByValue(String value) {
        // 如果数据为null，刷新数据。
        refreshIfNecessary();
        // 遍历所有键值对，找到值为 value 的键
        List<String> keys = new ArrayList<>();
        for (var entry : snapshot().entrySet()) {
            if (entry.getValue().equals(value)) {
                keys.add(entry.getKey());
            }
        }
        return keys;
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
        return List.copyOf(snapshot().keySet());
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
     * 复用原数据库事务与缓存任务，提交成功后失效当前 DTO 的缓存。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
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
         * @return 当前任务，供继续组合事务步骤
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
                addCacheTask(EasyDataDto.this::invalidate);
                return this;
            }
            // 如果不是String，尝试用Json序列化
            try {
                // 序列化
                String str = objectMapper.writeValueAsString(value);
                // 数据库写入
                addTransactionTask(() -> repository.updateDataObjectData(namespace, target, key, str));
                // 缓存写入
                addCacheTask(EasyDataDto.this::invalidate);
            } catch (JacksonException e) {
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
         * @return 当前任务，供继续组合事务步骤
         */
        @Override
        public IEasyDataDtoTaskConfig remove(String key) {
            // 如果数据为null，刷新数据。
            refreshIfNecessary();
            // 数据库写入
            addTransactionTask(() -> repository.deleteByNamespaceAndTargetAndKey(namespace, target, key));
            // 缓存写入
            addCacheTask(EasyDataDto.this::invalidate);
            return this;
        }
    }
}
