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
 * <br>2026/11/03：文档编辑、原写入和重载共用类型锁，运行发布与磁盘保存分开。
 * <br>2026/11/05：提供同一类型锁内的运行版本查询，不为模型校验创建文件文档。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.0
 * @version 4.8.2-l
 * @see IFileConfigData
 */
public interface IFileConfigService extends IReloadable {
    /**
     * 2026/11/05：只查询已加载缓存与发布版本，不读取磁盘、模板或发现配置类型。
     * 回调内可通过 get 或 requireCurrent 读取同一类型，不得等待网络或其他线程取得同一锁。
     *
     * @param type 已注册配置类型
     * @param action 同一类型发布锁内的短同步查询，不能保留借用锁的假设
     * @param <T> 返回类型
     * @return 查询结果，加载是否有效由版本状态或严格读取明确判断
     * @throws UnsupportedOperationException 当替换实现未提供运行版本查询时
     */
    default <T> T withRuntime(Class<? extends IFileConfigData> type,
            java.util.function.Function<org.zexnocs.teanekocore.file_config.FileConfigDocument.Stamp, T> action) {
        throw new UnsupportedOperationException("文件配置实现尚未提供运行版本查询。");
    }

    /**
     * 2026/11/03：只接受本地已注册类型，不接受浏览器文件路径。
     * 回调不能保留文档能力或等待其他线程再次获取同一类型锁。
     * @param type 已注册配置类型
     * @param action 短文件编辑或缓存发布回调，不执行网络请求
     * @param <T> 返回类型
     * @return 原类型锁内的实际结果
     * @throws UnsupportedOperationException 当替换实现未支持受限文档时
     */
    default <T> T withDocument(Class<? extends IFileConfigData> type,
            java.util.function.Function<org.zexnocs.teanekocore.file_config.FileConfigDocument, T> action) {
        throw new UnsupportedOperationException("文件配置实现尚未提供受限文档能力。");
    }
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
