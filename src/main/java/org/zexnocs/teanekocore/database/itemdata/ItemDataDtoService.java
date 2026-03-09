package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataConfigService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoService;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import org.zexnocs.teanekocore.database.itemdata.metadata.ItemMetadataScanner;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 物品数据传输对象服务类
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemDataDtoService implements IItemDataDtoService {
    private final ItemMetadataScanner itemMetadataScanner;
    private final ObjectMapper objectMapper;
    private final ILogger logger;
    private final IItemDataConfigService iItemDataConfigService;

    @Lazy
    @Autowired
    public ItemDataDtoService(ItemMetadataScanner itemMetadataScanner,
                              ILogger logger,
                              IItemDataConfigService iItemDataConfigService) {
        this.itemMetadataScanner = itemMetadataScanner;
        this.objectMapper = new ObjectMapper();
        this.logger = logger;
        this.iItemDataConfigService = iItemDataConfigService;
    }

    /**
     * 创建一个空的物品数据传输对象，不需要进行元数据的解析
     *
     * @param namespace 物品数据的命名空间
     * @param ownerId   物品数据的拥有者 UUID
     * @param type      物品数据的类型
     * @param clazz     物品元数据类型的类对象
     * @return 物品数据传输对象
     *
     */
    @Override
    public <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(String namespace, UUID ownerId, String type, Class<T> clazz) {
        return new ItemDataDTO<>(
                iItemDataConfigService,
                namespace,
                ownerId,
                type,
                0,
                null,
                clazz);
    }

    /**
     * 根据 ItemDataObject 创建对应的 ItemDataDTO
     * <br>需要解析元数据
     *
     * @param itemDataObject 物品数据对象
     * @param clazz          物品元数据类型的类对象
     * @return 物品数据传输对象
     * @throws InvalidMetadataTypeException 无效的物品元数据类型异常，可能出现的情况：
     *                                      - 元数据类型在扫描的类中未找到
     *                                      - clazz 与 itemDataObject 中的元数据类型不匹配
     *                                      - 元数据无法被正确解析
     */
    @Override
    public <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(ItemDataObject itemDataObject, Class<T> clazz)
            throws InvalidMetadataTypeException {
        var uuid = itemDataObject.getUuid();
        // 解析元数据
        T metadata = null;
        Class<?> metadataClass = null;
        var rawMetadataType = itemDataObject.getMetadataType();
        var rawMetadata = itemDataObject.getMetadata();
        if (rawMetadataType != null && rawMetadata != null) {
            metadataClass = itemMetadataScanner.getClassFromType(rawMetadataType);
            if (metadataClass == null) {
                throw new InvalidMetadataTypeException("""
                        物品数据 UUID: %s 的元数据类型 %s 无效，无法找到对应的类。"""
                        .formatted(uuid.toString(), rawMetadataType)
                );
            }
            // 判断 metadataClass 是否与 clazz 兼容，保证 metadataClass 是 clazz 的子类或实现类
            if (!clazz.isAssignableFrom(metadataClass)) {
                throw new InvalidMetadataTypeException("""
                        物品数据 UUID: %s 的元数据类型 %s 与预期的类型 %s 不兼容。"""
                        .formatted(uuid.toString(), rawMetadataType, clazz.getName())
                );
            }
            try {
                metadata = clazz.cast(objectMapper.readValue(rawMetadata, metadataClass));
            } catch (Exception e) {
                logger.errorWithReport("ItemDataService", """
                                解析物品数据 UUID: %s 的元数据时发生错误，元数据类型: %s。"""
                        .formatted(uuid.toString(), rawMetadataType), e);
            }
        }
        return new ItemDataDTO<>(
                iItemDataConfigService,
                itemDataObject.getNamespace(), itemDataObject.getOwnerId(),
                itemDataObject.getType(),
                itemDataObject.getCount(),
                metadata,
                metadataClass);
    }
}
