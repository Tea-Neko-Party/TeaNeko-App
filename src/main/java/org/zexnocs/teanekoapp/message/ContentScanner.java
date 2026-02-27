package org.zexnocs.teanekoapp.message;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于扫描带有 {@link TeaNekoContent} 和实现了 {@link ITeaNekoContent} 的类。
 *
 * @see TeaNekoContent
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Service
public class ContentScanner extends AbstractScanner {
    /// key → contentType
    private final Map<String, Class<? extends ITeaNekoContent>> contentClassMap = new ConcurrentHashMap<>();

    /// 类扫描器
    private final IClassScanner iClassScanner;
    private final ILogger logger;

    public ContentScanner(IClassScanner iClassScanner, ILogger logger) {
        super();
        this.iClassScanner = iClassScanner;
        this.logger = logger;
    }

    /**
     * 扫描方法。
     *
     */
    @Override
    protected void _scan() {
        contentClassMap.clear();
        var map = iClassScanner.getClassesWithAnnotationAndInterface(TeaNekoContent.class, ITeaNekoContent.class);
        for (var entry : map.entrySet()) {
            var clazz = entry.getKey();
            var annotation = entry.getValue();
            var key = annotation.value();
            // 鲁棒性
            if(key == null || key.isEmpty()) {
                logger.warn(this.getClass().getName(), """
                                %s 其 @TeaNekoContent 注解的 value 为空，请检查该类是否正确使用了注解。"""
                                .formatted(clazz.getName()));
            }
            var existing = contentClassMap.put(key, clazz);
            if(existing != null) {
                logger.warn(this.getClass().getName(), """
                                %s 和 %s 的 @TeaNekoContent 注解的 value 都是 %s，存在冲突，请检查这两个类是否正确使用了注解。"""
                                .formatted(existing.getName(), clazz.getName(), key));
            }
        }
    }

    /**
     * 根据 key 获取到对应的内容类。
     *
     * @param key 内容类型
     * @return 对应的内容类，如果不存在则返回 null。
     */
    @Nullable
    public Class<? extends ITeaNekoContent> getContentClass(String key) {
        if(key == null) return null;
        return contentClassMap.get(key);
    }
}
