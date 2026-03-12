package org.zexnocs.teanekocore.file_config.api;

import java.lang.annotation.*;

/**
 * 用于指定一个 {@link IFileConfigData} 的保存名字和路径。
 * <br>需要加在一个实现了 {@link IFileConfigData} 的类上。
 *
 * @see FileConfigType
 * @see IFileConfigData
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileConfig {
    /**
     * 文件名，将于 {@code type} 一起构造文件的文件名。
     * <br>例如 {@link FileConfigType#JSON} 和 value = "A"，则保存为 "A.json"
     *
     * @see FileConfigType
     * @return {@link String }
     */
    String value();

    /**
     * 相对项目或 jar 路径下 "config/" 的文件夹路径。
     * <br>空表示路径 "config/"。对于项目而言，"config" 文件夹 与 "src" 同级。
     * <br>例如
     * {@code "teaneko"}
     * 则保存在
     * {@code "config/teaeneko"}
     * 文件夹中。
     *
     * @return {@link String } 相对路径
     */
    String path() default "";

    /**
     * 文件保存类型。默认为
     * {@link FileConfigType#JSON}
     *
     * @return {@link FileConfigType } 文件保存类型。
     */
    FileConfigType type() default FileConfigType.JSON;
}
