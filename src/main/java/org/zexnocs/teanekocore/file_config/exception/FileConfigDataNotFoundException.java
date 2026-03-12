package org.zexnocs.teanekocore.file_config.exception;

import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 如果 {@link IFileConfigService} 未找到相应的 {@link IFileConfigData} 报出该异常；
 * 或者 {@link IFileConfigData} 未找到相应的 {@link FileConfig} 元信息报出该异常。
 * <br>可能的原因：
 * 1. {@link IFileConfigData} 没有实现 {@link FileConfig} 注解，或者有注解没有继承类
 * 2. {@link IFileConfigData} 没有默认无参构造器。
 *
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public class FileConfigDataNotFoundException extends RuntimeException {
    public FileConfigDataNotFoundException(String message) {
        super(message);
    }
}
