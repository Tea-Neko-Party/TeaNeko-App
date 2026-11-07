package org.zexnocs.teanekoapp.message;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布消息内容类型目录，不在消息处理路径发现类型。
 * <br>2026/11/06：标识冲突拒绝发布，失败重扫保留完整旧目录。
 * <br>2026/11/07：拒绝重复内容类型并原子发布完整目录。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 * @version 4.8.7-l
 */
@Service
public class ContentPartScanner implements IScanner {
    private volatile @Nullable Map<String, Class<? extends ITeaNekoContentPart>> types;
    private final IClassScanner classes;

    /** @param classes 仅初始化或显式重扫使用的类型发现设施 */
    public ContentPartScanner(IClassScanner classes) {
        this.classes = classes;
    }

    /** 首次成功后不再重复扫描。 */
    @Override
    public synchronized void init() {
        if (types == null) {
            reload();
        }
    }

    /**
     * 重扫在局部完成，失败时不发布任何部分结果。
     * @throws IllegalArgumentException 当内容标识为空或有重复声明时
     */
    @Override
    public synchronized void reload() {
        var next = new LinkedHashMap<String, Class<? extends ITeaNekoContentPart>>();
        for (var entry : classes.getClassesWithAnnotationAndInterface(TeaNekoContentPart.class, ITeaNekoContentPart.class).entrySet()) {
            String id = entry.getValue().value();
            var type = entry.getKey();
            if (id.isBlank()) {
                throw new IllegalArgumentException("内容类型标识为空，来源=" + type.getName());
            }
            var previous = next.putIfAbsent(id, type);
            if (previous != null) {
                throw new IllegalArgumentException("内容类型标识冲突：" + id + "，来源=" + previous.getName() + "、" + type.getName());
            }
        }
        types = Map.copyOf(next);
    }

    /**
     * @param key 内容类型标识
     * @return 对应类型，未登记时为空
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public @Nullable Class<? extends ITeaNekoContentPart> getContentClass(@Nullable String key) {
        var current = types;
        if (current == null) {
            throw new IllegalStateException("消息内容目录尚未就绪，请完成初始化或显式重扫。");
        }
        return key == null ? null : current.get(key);
    }
}
