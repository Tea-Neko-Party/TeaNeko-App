package org.zexnocs.teanekocore.file_config.api;

import java.io.Serializable;

/**
 * 文件配置数据接口，所有文件配置数据类都必须实现此接口。
 * <br>需要提供一个默认值，用于在本地保存没有数据时使用。
 * <br>需要加上
 * {@link FileConfig}
 * 注解
 * <br>需要提供无参数构造器来构造一个默认值。
 *
 * @author zExNocs
 * @date 2026/03/12
 *
 */
public interface IFileConfigData extends Serializable {
}
