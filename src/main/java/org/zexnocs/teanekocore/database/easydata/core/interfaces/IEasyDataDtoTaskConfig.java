package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.easydata.core.exception.JsonSerializationFailedException;

/**
 * EasyData DTO 任务配置接口。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IEasyDataDtoTaskConfig extends IDatabaseTaskConfig {
    /**
     * 设置值，并在数据库中进行更新。
     * 如果值为 null，则删除该键。
     * @param key 键。
     * @param value 值。
     * @param <T> 类型。
     */
    <T> IEasyDataDtoTaskConfig set(String key, @Nullable T value) throws JsonSerializationFailedException;

    /**
     * 设置布尔值。
     * @param key 键。
     * @param value 值。
     */
    default IEasyDataDtoTaskConfig setBoolean(String key, boolean value) throws JsonSerializationFailedException {
        return set(key, value);
    }

    /**
     * 删除键。
     * @param key 键。
     */
    IEasyDataDtoTaskConfig remove(String key);
}
