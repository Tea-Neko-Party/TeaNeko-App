package org.zexnocs.teanekoapp.client;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布带客户端声明的单例目录，动态连接和启用资格仍由调用方复核。
 * <br>2026/11/06：拒绝重复标识；完整重扫成功后原子替换，失败保留原快照。
 * <br>2026/11/07：拒绝重复客户端声明，重扫失败保留原目录。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 * @version 4.8.7-l
 */
@Service
public class TeaNekoClientScanner implements IScanner {
    private volatile @Nullable Map<String, Pair<TeaNekoClient, ITeaNekoClient>> handlers;
    private final IBeanScanner beans;

    /** @param beans 只在初始化和显式重扫中调用的 Bean 发现设施 */
    public TeaNekoClientScanner(IBeanScanner beans) {
        this.beans = beans;
    }

    /**
     * @return 不可变客户端快照
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public Map<String, Pair<TeaNekoClient, ITeaNekoClient>> getHandlerMap() {
        var current = handlers;
        if (current == null) {
            throw new IllegalStateException("客户端目录尚未就绪，请完成初始化或显式重扫。");
        }
        return current;
    }

    /**
     * @param key 客户端标识
     * @return 当前快照中的声明和单例，不存在时为空
     */
    public @Nullable Pair<TeaNekoClient, ITeaNekoClient> getPair(String key) {
        return getHandlerMap().get(key);
    }

    /** 首次成功后不重复发现，失败仅由初始化或显式重扫恢复。 */
    @Override
    public synchronized void init() {
        if (handlers == null) {
            reload();
        }
    }

    /**
     * 完整验证后一次发布；不清空读者正在使用的旧快照。
     * @throws IllegalArgumentException 当标识为空或有重复声明时
     */
    @Override
    public synchronized void reload() {
        var next = new LinkedHashMap<String, Pair<TeaNekoClient, ITeaNekoClient>>();
        var owners = new LinkedHashMap<String, String>();
        for (var entry : beans.getBeansWithAnnotationAndInterface(TeaNekoClient.class, ITeaNekoClient.class).entrySet()) {
            var pair = entry.getValue();
            if (!pair.first().enabled()) {
                continue;
            }
            String id = pair.first().value();
            if (id.isBlank()) {
                throw new IllegalArgumentException("客户端标识为空，来源=" + entry.getKey());
            }
            var previous = owners.putIfAbsent(id, entry.getKey());
            if (previous != null) {
                throw new IllegalArgumentException("客户端标识冲突：" + id + "，来源=" + previous + "、" + entry.getKey());
            }
            next.put(id, pair);
        }
        handlers = Map.copyOf(next);
    }
}
