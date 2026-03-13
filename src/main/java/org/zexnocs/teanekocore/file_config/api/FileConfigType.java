package org.zexnocs.teanekocore.file_config.api;

/**
 * 在本地保存 config data 的类型。
 *
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public enum FileConfigType {
    /**
     * .json 文件
     * <br>允许解析时使用 "#" 注释
     */
    JSON,

    /**
     * .yaml 文件
     */
    YAML,
}
