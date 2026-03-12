package org.zexnocs.teanekocore.file_config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.interfaces.FileTypeParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于注册 实现了 {@link FileTypeParser} 和 {@link IFileTypeParser} 的类。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@Service
@RequiredArgsConstructor
public class FileTypeScanner extends AbstractScanner {
    /// type → parser 的映射
    private final Map<FileConfigType, IFileTypeParser> parserMap = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;

    /**
     * 扫描方法。
     *
     */
    @Override
    protected void _scan() {
        var pairs = iBeanScanner.getBeansWithAnnotationAndInterface(FileTypeParser.class, IFileTypeParser.class);
        for(var pair: pairs.values()) {
            var annotation = pair.first();
            var bean = pair.second();
            var v = parserMap.putIfAbsent(annotation.value(), bean);
            if(v != null) {
                iLogger.warn(this.getClass().getSimpleName(), """
                    %s 具有多个解析器:
                    1. %s
                    2. %s
                    已使用 1 号解析器。
                    """.formatted(annotation.value(),
                        v.getClass().getName(),
                        bean.getClass().getName()));
            }
        }
    }

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        parserMap.clear();
    }

    /**
     * 根据 type 获取相应的解析器。
     *
     * @see FileConfigType
     * @param fileConfigType type
     * @return {@link IFileTypeParser }
     */
    @Nullable
    public IFileTypeParser get(@NonNull FileConfigType fileConfigType) {
        return parserMap.get(fileConfigType);
    }
}
