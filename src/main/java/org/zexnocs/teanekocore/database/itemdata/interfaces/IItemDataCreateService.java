package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.springframework.jmx.export.metadata.InvalidMetadataException;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import tools.jackson.core.JacksonException;

import java.util.UUID;

/**
 * 物品数据创建服务接口
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataCreateService {
    /**
     * 在数据库事务中创建一个新的物品数据记录
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @param count 初始数量
     * @param metadata 物品元数据
     * @return 新创建的物品数据传输对象
     */
    ItemDataObject createIfAbsent(
            UUID ownerId,
            String namespace,
            String type,
            int count,
            IItemMetadata metadata
    ) throws ClassCastException, InvalidMetadataException, JacksonException;
}
