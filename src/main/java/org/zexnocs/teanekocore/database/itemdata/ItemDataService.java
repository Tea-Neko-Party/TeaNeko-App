package org.zexnocs.teanekocore.database.itemdata;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.metadata.InvalidMetadataException;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.exception.ItemDataNotFoundException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.*;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.core.JacksonException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品数据服务类，提供对物品数据的获取和创建功能。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemDataService implements IItemDataService {
    private final ItemDataRepository itemDataRepository;
    private final IItemDataCacheService iItemDataCacheService;
    private final IDatabaseService databaseService;
    private final IItemDataCreateService iItemDataCreateService;
    private final IItemDataDtoService iItemDataDtoService;
    private final ILogger logger;

    @Autowired
    public ItemDataService(ItemDataRepository itemDataRepository,
                           IItemDataCacheService iItemDataCacheService,
                           IDatabaseService databaseService,
                           IItemDataCreateService
                           iItemDataCreateService, IItemDataDtoService iItemDataDtoService, ILogger logger) {
        this.itemDataRepository = itemDataRepository;
        this.iItemDataCacheService = iItemDataCacheService;
        this.databaseService = databaseService;
        this.iItemDataCreateService = iItemDataCreateService;
        this.iItemDataDtoService = iItemDataDtoService;
        this.logger = logger;
    }

    /**
     * 根据 UUID 和元数据类型类获取物品数据传输对象
     * @param uuid 物品唯一标识符
     * @param clazz 物品元数据类型类
     * @return 物品数据传输对象
     * @param <T> 物品元数据类型
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends IItemMetadata> IItemDataDTO<T> getByUuid(UUID uuid, Class<T> clazz) {
        var itemData = getByUuid(uuid);
        try {
            return (IItemDataDTO<T>) itemData;
        } catch (ClassCastException e) {
            throw new ClassCastException("""
                    物品数据 UUID: %s 的元数据类型与请求的类型不匹配。请求的类型: %s，实际类型: %s。"""
                    .formatted(
                    uuid.toString(),
                    clazz.getName(),
                    itemData.getMetadataClass() != null ? itemData.getMetadataClass().getName() : "null")
            );
        }
    }

    /**
     * 获取或创建一个新的物品数据传输对象
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @param count 初始数量
     * @param metadata 物品元数据
     * @return 新创建的物品数据传输对象
     * @param <T> 物品元数据类型
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IItemMetadata> TaskFuture<IItemDataDTO<T>> getOrCreate(UUID ownerId,
                                                                             String namespace,
                                                                             String type,
                                                                             int count,
                                                                             @Nullable T metadata) {
        String className;
        if(metadata == null) {
            className = "null";
        } else {
            className = metadata.getClass().getName();
        }

        var future = new TaskFuture<IItemDataDTO<T>>(logger, "创建或获取物品 (ownerId: %s, namespace: %s, type: %s)"
                .formatted(ownerId, namespace, type), new CompletableFuture<>());
        // 先从已有的缓存或数据库中获取
        var cache = getByOwnerNamespaceType(ownerId, namespace, type);
        if(cache != null) {
            try {
                future.complete((IItemDataDTO<T>) cache);
            } catch (ClassCastException e) {
                future.completeExceptionally(new ClassCastException("""
                        物品数据 (ownerId: %s, namespace: %s, type: %s) 的元数据类型与请求的类型不匹配。
                        请求的类型: %s，实际类型: %s。"""
                        .formatted(
                        ownerId.toString(),
                        namespace,
                        type,
                        className,
                        cache.getMetadataClass() != null ? cache.getMetadataClass().getName() : "null")
                ));
            }
            return future;
        }
        // 数据库中也没有，则创建一个新的物品数据
        final ItemDataObject[] newItemDataObject = new ItemDataObject[1];
        databaseService.pushQuickTask("""
                创建新物品数据
                ownerId: %s,
                namespace: %s,
                type: %s""".formatted(ownerId.toString(), namespace, type),
                () -> {
                    // 事务中创建物品数据
                    try {
                        newItemDataObject[0] = iItemDataCreateService
                                .createIfAbsent(ownerId, namespace, type, count, metadata);
                    } catch (JacksonException e) {
                        var newE = new RuntimeException("""
                                创建物品数据 (ownerId: %s, namespace: %s, type: %s) 失败，发生 JSON 处理异常。"""
                                .formatted(ownerId.toString(), namespace, type), e);
                        future.completeExceptionally(newE);
                        // 抛出异常以退回事务
                        throw newE;
                    } catch (ClassCastException e) {
                        var newE = new RuntimeException("""
                                创建的物品数据 (ownerId: %s, namespace: %s, type: %s) 的元数据类型与请求的类型不匹配。请求的类型: %s，实际类型: %s。
                                """.formatted(
                                ownerId.toString(),
                                namespace,
                                type,
                                className, e));
                        future.completeExceptionally(newE);
                        // 抛出异常以退回事务
                        throw newE;
                    } catch (InvalidMetadataException e) {
                        var newE = new RuntimeException("""
                                创建的物品数据 (ownerId: %s, namespace: %s, type: %s) 的元数据无效。"""
                                .formatted(ownerId.toString(), namespace, type), e);
                        future.completeExceptionally(newE);
                        // 抛出异常以退回事务
                        throw newE;
                    }
                },
                () -> {
                    // 创建缓存
                    Class<T> metadataClass;
                    if(metadata == null) {
                        metadataClass = null;
                    } else {
                        metadataClass = (Class<T>) metadata.getClass();
                    }
                    var dto = iItemDataDtoService.createItemDataDto(newItemDataObject[0],
                            metadata,
                            metadataClass);
                    iItemDataCacheService.createCache(dto);
                    iItemDataCacheService.addToOwnerNamespace2UUIDsCache(ownerId, namespace, type, dto.getUuid());
                    // 提交 future
                    future.complete(dto);
                });
        return future;
    }

    /**
     * 获取 ownerId 下对应的 namespace 全部物品数据传输对象列表
     *
     * @param ownerId   拥有者 ID
     * @param namespace 命名空间
     * @return 物品数据传输对象列表
     */
    @Override
    public @NonNull Map<String, IItemDataDTO<?>> getMapByOwnerNamespace(UUID ownerId,
                                                                        String namespace) {
        // 尝试从缓存中获取该 (ownerId, namespace) 下的 UUID 列表
        var type2Uuid = iItemDataCacheService.getUUIDsByOwnerNamespace(ownerId, namespace);
        if(type2Uuid != null) {
            Map<String, IItemDataDTO<?>> result = new ConcurrentHashMap<>();
            for (var entry : type2Uuid.entrySet()) {
                var dto = getByUuid(entry.getValue());
                result.put(entry.getKey(), dto);
            }
            return result;
        }
        // ----------------------
        // 否则从数据库中获取
        var itemDataObjects = itemDataRepository.findAllByOwnerIdAndNamespace(ownerId, namespace);
        // 构建 UUID 列表缓存和输出列表
        var caches = new ConcurrentHashMap<String, UUID>();
        var result = new ConcurrentHashMap<String, IItemDataDTO<?>>();
        for(var object: itemDataObjects) {
            var uuid = object.getUuid();
            caches.put(object.getType(), uuid);
            // 从缓存或者数据库中获取 DTO
            var dto = getByUuid(uuid);
            result.put(object.getType(), dto);
        }
        // 放入缓存
        iItemDataCacheService.createOwnerNamespace2UUIDsCache(ownerId, namespace, caches);
        return result;
    }

    /**
     * 获取 namespace 下对应 type 的所有物品数据传输对象映射
     * @param namespace 命名空间
     * @param type 物品类型
     * @return ownerId → 物品数据传输对象 映射。
     */
    @Override
    public @NonNull Map<UUID, IItemDataDTO<?>> getMapByNamespaceType(String namespace, String type) {
        var itemDataObjects = itemDataRepository.findAllByNamespaceAndType(namespace, type);
        var result = new ConcurrentHashMap<UUID, IItemDataDTO<?>>();
        for(var object: itemDataObjects) {
            var uuid = object.getUuid();
            var dto = getByUuid(uuid);
            result.put(object.getOwnerId(), dto);
        }
        return result;
    }

    /**
     * 获取 ownerId 下对应的 namespace 下 type 类型的物品数据传输对象列表
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @return 物品数据传输对象列表。如果不存在则返回 null
     */
    @Nullable
    @Override
    public IItemDataDTO<?> getByOwnerNamespaceType(UUID ownerId,
                                                   String namespace,
                                                   String type) {
        // 直接获取缓存该 namespace 下的全部物品数据传输对象列表
        var map = getMapByOwnerNamespace(ownerId, namespace);
        return map.get(type);
    }

    /**
     * 根据 UUID 获取物品数据传输对象任务配置
     * @param uuid 物品唯一标识符
     * @return 物品数据传输对象任务配置
     * @throws InvalidMetadataTypeException 无效的元数据类型异常
     * @throws ItemDataNotFoundException 物品数据未找到异常
     */
    public IItemDataDTO<?> getByUuid(UUID uuid)
        throws InvalidMetadataTypeException, ItemDataNotFoundException {
        // 尝试从缓存中获取
        var cachedDto = iItemDataCacheService.getDTOByUUID(uuid);
        if (cachedDto != null) {
            return cachedDto;
        }

        // 否则从数据库中获取
        var itemDataOptional = itemDataRepository.findById(uuid);
        if (itemDataOptional.isEmpty()) {
            throw new ItemDataNotFoundException("""
                    未找到对应 UUID: %s 的物品数据。""".formatted(uuid.toString())
            );
        }
        var itemDataObject = itemDataOptional.get();
        var dto = iItemDataDtoService.createItemDataDto(itemDataObject);
        // 放入缓存
        iItemDataCacheService.createCache(dto);
        return dto;
    }
}
