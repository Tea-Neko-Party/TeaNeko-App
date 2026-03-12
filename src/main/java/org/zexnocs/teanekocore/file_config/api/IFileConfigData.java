package org.zexnocs.teanekocore.file_config.api;

/**
 * 文件配置数据接口，所有文件配置数据类都必须实现此接口。
 * <br>需要提供一个默认值，用于在本地保存没有数据时使用。
 * <br>需要加上 {@link FileConfig} 注解
 *
 * @see FileConfig
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public interface IFileConfigData {
    /**
     * 获取默认值。
     *
     * @return {@link IFileConfigData }
     */
    IFileConfigData getDefault();
}
