package org.zexnocs.teanekocore.database.itemdata.data;

import lombok.Getter;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataConfigService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可以直接使用的物品数据传输对象
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ItemDataDTO<T extends IItemMetadata> implements IItemDataDTO<T> {
    /// 所属的服务
    private final IItemDataConfigService service;

    /// 物品唯一标识符
    @Getter
    private final UUID uuid;

    /// 物品拥有者的唯一标识符
    @Getter
    private final UUID ownerId;

    /// 物品命名空间
    @Getter
    private final String namespace;

    /// 物品类型键
    @Getter
    private final String type;

    /// 物品元数据
    @Getter
    private T metadata;

    /// 元数据的类类型
    @Getter
    private final Class<?> metadataClass;

    /// 物品数量
    private final AtomicInteger count;

    /**
     * 构造函数
     */
    public ItemDataDTO(IItemDataConfigService service,
                       UUID uuid,
                       UUID ownerId,
                       String namespace,
                       String type,
                       int count,
                       T metadata,
                       Class<?> metadataClass)
    {
            this.service = service;
            this.uuid = uuid;
            this.ownerId = ownerId;
            this.namespace = namespace;
            this.type = type;
            this.metadata = metadata;
            this.metadataClass = metadataClass;
            this.count = new AtomicInteger(count);
    }

    /**
     * 获取物品数量的原子操作对象
     * @return 物品数量的原子操作对象
     */
    protected AtomicInteger getCountAtomic() {
        return count;
    }

    /**
     * 设置 metaData
     * @param metadata 新的 metaData
     */
    protected void setMetadata(T metadata) {
        this.metadata = metadata;
    }


    /**
     * 获取物品数量
     * @return 物品数量
     */
    @Override
    public int getCount() {
        return count.get();
    }

    /**
     * 获取数据库任务配置
     * @param taskName 任务名称
     * @return 数据库任务配置
     */
    @Override
    public IItemDataDtoTaskConfig<T> getDatabaseTaskConfig(String taskName) {
        return service.buildDatabaseTaskConfig(this, taskName);
    }
}
