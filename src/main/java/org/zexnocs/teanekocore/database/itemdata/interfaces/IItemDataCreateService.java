package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;

/**
 * 物品数据创建服务接口
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataCreateService {
    /**
     * 在数据库事务中创建一个新的物品数据记录。
     * <br>默认数量为 0，metadata 为 null。
     *
     * @see IItemDataDtoService
     * @param dto 物品数据传输对象，包含了物品的 uuid、数量和元数据等信息。
     * @return 新创建的物品数据传输对象
     * @throws InvalidMetadataTypeException 无效的物品元数据类型异常
     */
    ItemDataObject createIfAbsent(ItemDataDTO<?> dto) throws InvalidMetadataTypeException;
}
