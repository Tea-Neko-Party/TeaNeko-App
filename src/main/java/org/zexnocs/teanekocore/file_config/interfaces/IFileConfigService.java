package org.zexnocs.teanekocore.file_config.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;

/**
 * 使用 {@link IFileConfigData} 的类来获取实例。
 *
 * @see IFileConfigData
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public interface IFileConfigService {
    /**
     * 获取 config 实例
     *
     * @param clazz 需要返回的 config class
     * @return config 实例
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data
     */
    @NonNull
    <T extends IFileConfigData> T get(Class<T> clazz) throws FileConfigDataNotFoundException;

    /**
     * 写入一个 config 文件
     *
     * @param data 需要写入的 config data
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data 的信息
     */
    void write(IFileConfigData data) throws FileConfigDataNotFoundException;
}
