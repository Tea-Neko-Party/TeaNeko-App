package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.UUID;

/**
 * 创建 itemDataDto 服务
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 */
public interface IItemDataDtoService {
    /**
     * 创建一个新的物品数据传输对象<br>
     * 不需要进行解析，没有 UUID、metadata、数量。
     *
     * @param namespace 物品数据的命名空间
     * @param ownerId   物品数据的拥有者 UUID
     * @param type      物品数据的类型
     * @param clazz     物品元数据类型的类对象
     * @return 物品数据传输对象
     *
     */
    <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(String namespace, UUID ownerId, String type, Class<T> clazz);

    /**
     * 根据 ItemDataObject 创建对应的 ItemDataDTO
     * <br>需要解析元数据
     *
     * @param itemDataObject 物品数据对象
     * @param clazz          物品元数据类型的类对象
     * @return 物品数据传输对象
     * @throws InvalidMetadataTypeException 无效的物品元数据类型异常，可能出现的情况：
     * - 元数据类型在扫描的类中未找到
     * - clazz 与 itemDataObject 中的元数据类型不匹配
     * - 元数据无法被正确解析
     */
    <T extends IItemMetadata> ItemDataDTO<T> createItemDataDto(ItemDataObject itemDataObject, Class<T> clazz)
            throws InvalidMetadataTypeException;
}
