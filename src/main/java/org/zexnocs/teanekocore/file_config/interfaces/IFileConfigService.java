package org.zexnocs.teanekocore.file_config.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.reload.api.IReloadable;

import java.io.IOException;

/**
 * 使用 {@link IFileConfigData} 的类来获取实例。
 * <br>如果要在构造器里寻找 config，需要依赖类 {@link org.zexnocs.teanekocore.reload.interfaces.IReloadService}
 * <br>或者使用 {@code @EventListener(ApplicationReadyEvent.class)}
 *
 * @see IFileConfigData
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public interface IFileConfigService extends IReloadable {
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
     * @throws IOException 写入失败
     */
    void write(IFileConfigData data) throws FileConfigDataNotFoundException, IOException;
}
