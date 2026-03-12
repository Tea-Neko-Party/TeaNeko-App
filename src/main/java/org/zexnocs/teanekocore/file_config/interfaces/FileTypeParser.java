package org.zexnocs.teanekocore.file_config.interfaces;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 根据 {@link FileConfigType} 来解析文件类型的注解。
 * <br>需要实现 {@link IFileTypeParser} 接口。
 *
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FileTypeParser {
    /**
     * 解析的 type。
     * <br>一个 {@link FileConfigType} 只允许一个 {@link IFileTypeParser} 来解析。
     *
     * @return {@link FileConfigType }
     */
    FileConfigType value();
}
