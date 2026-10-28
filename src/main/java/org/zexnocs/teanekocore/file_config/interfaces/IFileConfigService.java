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
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.0
 * @version 4.5.0-l
 * @see IFileConfigData
 */
public interface IFileConfigService extends IReloadable {
    /**
     * 获取 config 实例
     *
     * @param clazz 需要返回的 config class
     * @param <T> 配置数据类型
     * @return config 实例
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data
     */
    @NonNull
    <T extends IFileConfigData> T get(Class<T> clazz) throws FileConfigDataNotFoundException;

    /**
     * 只读取最近一次已完成加载成功的配置，不能在加载失败后沿用旧缓存冒充成功。
     * get 保留最后有效值的兼容语义；需要严格失败边界的模块使用此入口。
     * @param clazz 配置类型
     * @param <T> 配置数据类型
     * @return 当前加载成功的数据
     * @throws FileConfigDataNotFoundException 当从未加载成功或最近加载失败时
     * @throws UnsupportedOperationException 当自定义实现尚未提供当前状态检查时
     */
    default <T extends IFileConfigData> T requireCurrent(Class<T> clazz) throws FileConfigDataNotFoundException {
        throw new UnsupportedOperationException("文件配置实现尚未提供当前加载状态检查。");
    }

    /**
     * 写入一个 config 文件
     *
     * @param data 需要写入的 config data
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data 的信息
     * @throws IOException 写入失败
     */
    void write(IFileConfigData data) throws FileConfigDataNotFoundException, IOException;
}
