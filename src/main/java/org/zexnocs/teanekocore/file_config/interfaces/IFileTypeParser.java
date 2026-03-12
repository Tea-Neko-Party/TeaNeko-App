package org.zexnocs.teanekocore.file_config.interfaces;

import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 根据 {@link}
 * 需要加上 {@link FileTypeParser} 注解来标注解析的哪一种类型。
 *
 * @author zExNocs
 * @date 2026/03/12
 * @since 4.2.0
 */
public interface IFileTypeParser {

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param clazz  要解析的类
     * @return {@link T }
     */
    <T extends IFileConfigData> T fromFile(InputStream stream, Class<T> clazz);

    /**
     * 根据 IFileConfigData 来写入到文件中。
     *
     * @param path     路径
     * @param data     数据
     */
    void fromData(Path path, IFileConfigData data);
}
