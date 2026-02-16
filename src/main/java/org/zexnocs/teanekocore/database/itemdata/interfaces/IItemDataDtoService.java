package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

/**
 * 创建 itemDataDto 服务
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataDtoService {
    /**
     * 根据 ItemDataObject 创建对应的 ItemDataDTO
     * 指定元数据类型，无需再解析
     * @param itemDataObject 物品数据对象
     * @param metadata 物品元数据
     * @param metadataClass 物品元数据类
     * @return 物品数据传输对象
     */
    <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(ItemDataObject itemDataObject,
                                                               T metadata,
                                                               Class<T> metadataClass);
    /**
     * 根据 ItemDataObject 创建对应的 ItemDataDTO
     * 需要解析元数据
     * @param itemDataObject 物品数据对象
     * @return 物品数据传输对象
     * @throws InvalidMetadataTypeException 元数据类型无效异常
     */
    ItemDataDTO<?> createItemDataDto(ItemDataObject itemDataObject) throws InvalidMetadataTypeException;
}
