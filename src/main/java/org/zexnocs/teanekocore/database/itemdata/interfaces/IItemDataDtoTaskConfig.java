package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import tools.jackson.core.JacksonException;

/**
 * 数据库任务配置接口
 * 注意，在使用 reduceCount 方法减少物品数量时，应确保传入的数量为非负数。
 * 在需要时通过 pushWithFuture 来获取 Future 对象，并在 Future 中捕获异常 InsufficientItemCountException。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataDtoTaskConfig<T extends IItemMetadata> extends IDatabaseTaskConfig {
    /**
     * 增加物品数量
     * @param amount 增加的数量
     * @return 当前任务配置对象
     * @throws IllegalArgumentException 如果增加的数量为负数
     */
    IItemDataDtoTaskConfig<T> addCount(int amount) throws IllegalArgumentException;

    /**
     * 减少物品数量
     * 此外会在数据库线程中二次验证物品数量是否足够，如果不足够会在 Future 中抛出 InsufficientItemCountException 异常。
     * 如果要处理该异常，请使用 pushWithFuture 来获取 Future 对象，并在 Future 中捕获该异常。
     * @param amount 减少的数量
     * @return 当前任务配置对象
     * @throws IllegalArgumentException 如果减少的数量为负数
     * @see org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException
     */
    IItemDataDtoTaskConfig<T> reduceCount(int amount)
            throws IllegalArgumentException;

    /**
     * 设置物品数量
     * @param count 新的数量
     * @return 当前任务配置对象
     * @throws IllegalArgumentException 如果数量为负数
     */
    IItemDataDtoTaskConfig<T> setCount(int count) throws IllegalArgumentException;

    /**
     * 修改物品元数据
     * @param metaData 新的元数据
     * @return 当前任务配置对象
     * @throws JacksonException 如果元数据序列化失败
     */
    IItemDataDtoTaskConfig<T> setMetaData(T metaData) throws JacksonException;
}
