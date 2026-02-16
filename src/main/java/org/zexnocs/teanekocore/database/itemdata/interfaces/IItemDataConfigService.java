package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

/**
 * 用于根据 ItemDataDTO → ItemDataTaskConfig 的创建
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataConfigService {

    /**
     * 构建物品数据传输对象任务配置
     * @param itemDataDTO 物品数据传输对象
     * @param taskName 任务名称
     * @return 物品数据传输对象任务配置
     * @param <T> 物品元数据类型
     */
    <T extends IItemMetadata> IItemDataDtoTaskConfig<T> buildDatabaseTaskConfig(ItemDataDTO<T> itemDataDTO,
                                                                                String taskName);
}
