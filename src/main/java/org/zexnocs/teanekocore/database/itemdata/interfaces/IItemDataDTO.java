package org.zexnocs.teanekocore.database.itemdata.interfaces;

import jakarta.annotation.Nullable;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.UUID;

/**
 * 数据传输对象接口
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataDTO<T extends IItemMetadata> {
    /**
     * 获取 UUID
     * @return 物品 UUID
     */
    UUID getUuid();

    /**
     * 获取拥有者 ID
     * @return 拥有者 UUID
     */
    UUID getOwnerId();

    /**
     * 获取命名空间
     * @return 命名空间
     */
    String getNamespace();

    /**
     * 获取类型
     * @return 类型
     */
    String getType();

    /**
     * 获取元数据
     * @return 元数据
     */
    @Nullable
    T getMetadata();

    /**
     * 获取元数据类类型
     * @return 元数据类类型
     */
    Class<?> getMetadataClass();

    /**
     * 获取物品数量
     * @return 物品数量
     */
    int getCount();

    /**
     * 获取数据库任务用于上传数据到数据库
     * @param taskName 任务名称
     * @return 数据库任务配置接口
     */
    IItemDataDtoTaskConfig<T> getDatabaseTaskConfig(String taskName);
}
