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
     * 创建物品数据传输对象
     * 不需要进行元数据的解析
     * @param itemDataObject 物品数据对象
     * @param metadata 物品元数据
     * @param metadataClass 物品元数据类
     * @return 物品数据传输对象
     * @param <T> 物品元数据类型
     */
    @Override
    public <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(ItemDataObject itemDataObject,
                                                                      T metadata,
                                                                      Class<T> metadataClass) {
        return new ItemDataDTO<>(
                iItemDataConfigService,
                itemDataObject.getUuid(),
                itemDataObject.getOwnerId(),
                itemDataObject.getNamespace(),
                itemDataObject.getType(),
                itemDataObject.getCount(),
                metadata,
                metadataClass);
    }

    /**
     * 创建物品数据传输对象
     * 会进行元数据的解析
     * @param itemDataObject 物品数据对象
     * @return 物品数据传输对象
     * @throws InvalidMetadataTypeException 元数据类型无效异常
     */
    @Override
    public ItemDataDTO<?> createItemDataDto(ItemDataObject itemDataObject)
            throws InvalidMetadataTypeException {
        var uuid = itemDataObject.getUuid();
        // 解析元数据
        IItemMetadata metadata = null;
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
            try {
                metadata = (IItemMetadata) objectMapper.readValue(rawMetadata, metadataClass);
            } catch (Exception e) {
                logger.errorWithReport("ItemDataService", """
                                解析物品数据 UUID: %s 的元数据时发生错误，元数据类型: %s。"""
                                .formatted(uuid.toString(), rawMetadataType), e);
            }
        }
        return new ItemDataDTO<>(
                iItemDataConfigService,
                uuid,
                itemDataObject.getOwnerId(),
                itemDataObject.getNamespace(),
                itemDataObject.getType(),
                itemDataObject.getCount(),
                metadata,
                metadataClass);
    }
}
