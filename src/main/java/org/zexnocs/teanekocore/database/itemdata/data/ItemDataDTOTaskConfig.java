package org.zexnocs.teanekocore.database.itemdata.data;

import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.itemdata.ItemDataRepository;
import org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCreateService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 物品数据传输对象的数据库任务配置实现。
 * <br>4.1.3: 使用 config 作为创建新 itemData 对象，而不是在 service 中。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 * @version 4.1.3
 */
public class ItemDataDTOTaskConfig<T extends IItemMetadata>
        extends DatabaseTaskConfig implements IItemDataDtoTaskConfig<T> {
    /// 物品数据传输对象，包含了物品的 uuid、数量和元数据等信息。
    private final ItemDataDTO<T> itemDataDTO;

    /// 物品数据仓库，用于执行数据库操作。
    private final ItemDataRepository itemDataRepository;

    /// 对象映射器，用于将元数据对象转换为 JSON 字符串存储到数据库中。
    private final ObjectMapper objectMapper;

    /// uuid 指针，用于在事务任务中获取 uuid 参数，避免 lambda 捕获导致的参数问题。
    private final UUID[] uuids = new UUID[1];

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
                                 IItemDataCreateService itemDataCreateService,
                                 String taskName) {
        super(databaseService, taskName);
        this.itemDataRepository = itemDataRepository;
        this.itemDataDTO = itemDataDTO;
        this.objectMapper = objectMapper;
        // 第一步先确保该物品数据对象存在，如果不存在则创建一个新的对象。
        addTransactionTask(() -> {
            var created = itemDataCreateService.createIfAbsent(itemDataDTO);
            // 更新 uuid 指针，供后续任务使用。
            uuids[0] = created.getUuid();
        });
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
        addTransactionTask(() -> itemDataRepository.incrementItemCount(uuids[0], amount));
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
            var number = itemDataRepository.decrementItemCount(uuids[0], amount);
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
        addTransactionTask(() -> itemDataRepository.safeUpdateCount(uuids[0], count));

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
        addTransactionTask(() -> itemDataRepository.updateMetadata(uuids[0], metaDataJson));
        // 缓存更新
        addCacheTask(() -> itemDataDTO.setMetadata(metaData));
        return this;
    }
}
