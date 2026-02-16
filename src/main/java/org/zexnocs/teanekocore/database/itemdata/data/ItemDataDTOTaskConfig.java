package org.zexnocs.teanekocore.database.itemdata.data;

import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.itemdata.ItemDataRepository;
import org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 物品数据传输对象的数据库任务配置实现。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public class ItemDataDTOTaskConfig<T extends IItemMetadata>
        extends DatabaseTaskConfig implements IItemDataDtoTaskConfig<T> {
    private final ItemDataDTO<T> itemDataDTO;

    private final ItemDataRepository itemDataRepository;

    private final ObjectMapper objectMapper;

    /**
     * 使用默认的任务阶段链创建数据库任务配置。
     *
     * @param databaseService 数据库服务
     * @param itemDataRepository 物品数据仓库
     * @param objectMapper   对象映射器
     * @param itemDataDTO     物品数据传输对象
     * @param taskName        任务名称
     */
    public ItemDataDTOTaskConfig(IDatabaseService databaseService,
                                 ItemDataRepository itemDataRepository,
                                 ObjectMapper objectMapper,
                                 ItemDataDTO<T> itemDataDTO,
                                 String taskName) {
        super(databaseService, taskName);
        this.itemDataRepository = itemDataRepository;
        this.itemDataDTO = itemDataDTO;
        this.objectMapper = objectMapper;
    }

    /**
     * 增加物品数量
     * 如果要添加callback，直接使用 addCacheTask() 方法添加。
     * @param amount 增加的数量
     * @return 当前任务配置对象
     * @throws IllegalArgumentException 如果增加的数量为负数
     */
    @Override
    public IItemDataDtoTaskConfig<T> addCount(int amount) throws IllegalArgumentException {
        if(amount <= 0) {
            throw new IllegalArgumentException("要加的数量必须是正数。");
        }
        // 数据库写入
        addTransactionTask(() -> itemDataRepository.incrementItemCount(itemDataDTO.getUuid(), amount));
        // 缓存更新
        addCacheTask(() -> itemDataDTO.getCountAtomic().addAndGet(amount));
        return this;
    }

    /**
     * 减少物品数量
     * 如果减少失败，即数量不足时，则会抛出异常让事务回滚。
     * 如果想要添加成功的 callback，调用后该方法后直接使用 addCacheTask() 和 addTransactionTask() 方法添加普通和数据库任务。
     * 如果想要添加失败的callback，则 setExceptionHandler 来处理 InsufficientItemCountException 异常。
     * 处理后的异常不会被 report，但依然会回滚事务。
     * @param amount 减少的数量
     * @return 当前任务配置对象
     * @throws IllegalArgumentException 如果减少的数量为负数
     */
    @Override
    public IItemDataDtoTaskConfig<T> reduceCount(int amount)
            throws IllegalArgumentException{
        // 参数不合法
        if(amount <= 0) {
            throw new IllegalArgumentException("要减的数量必须是正数。");
        }

        // 数据库写入
        addTransactionTask(() -> {
            var number = itemDataRepository.decrementItemCount(itemDataDTO.getUuid(), amount);
            if(number <= 0) {
                throw new InsufficientItemCountException(String.format("""
                        数量不足，当前数量：%d，尝试减少数量：%d""",
                        itemDataDTO.getCount(), amount));
            }
        });

        // 缓存更新
        addCacheTask(() -> itemDataDTO.getCountAtomic().addAndGet(-amount));
        return this;
    }

    /**
     * 设置物品数量
     * @param count 新的数量
     * @return 当前任务配置对象
     */
    @Override
    public IItemDataDtoTaskConfig<T> setCount(int count) {
        if(count < 0) {
            throw new IllegalArgumentException("物品数量不能为负数。");
        }
        // 数据库写入
        addTransactionTask(() -> itemDataRepository.safeUpdateCount(itemDataDTO.getUuid(), count));

        // 缓存更新
        addCacheTask(() -> itemDataDTO.getCountAtomic().set(count));
        return this;
    }

    /**
     * 修改物品元数据
     * @param metaData 新的元数据
     * @return 当前任务配置对象
     * @throws JacksonException 如果元数据序列化失败
     */
    @Override
    public IItemDataDtoTaskConfig<T> setMetaData(T metaData) throws JacksonException {
        String metaDataJson = objectMapper.writeValueAsString(metaData);
        // 数据库写入
        addTransactionTask(() -> itemDataRepository.updateMetadata(itemDataDTO.getUuid(), metaDataJson));
        // 缓存更新
        addCacheTask(() -> itemDataDTO.setMetadata(metaData));
        return this;
    }
}
