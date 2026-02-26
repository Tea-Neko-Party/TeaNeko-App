package org.zexnocs.teanekoapp.client;

import lombok.Getter;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责扫描和注册所有标记了 {@link TeaNekoClient} 注解实现了 {@link ITeaNekoClient} 的类，并将它们注册到 TeaNeko 客户端中。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
@Service
public class TeaNekoClientScanner extends AbstractScanner {
    /// 处理器映射，key 是客户端名称，value 是一个 Pair，包含了注解和实例
    @Getter
    private final Map<String, Pair<TeaNekoClient, ITeaNekoClient>> handlerMap = new ConcurrentHashMap<>();

    /// bean 扫描器
    private final IBeanScanner iBeanScanner;

    public TeaNekoClientScanner(IBeanScanner iBeanScanner) {
        super();
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 扫描方法。
     *
     */
    @Override
    protected synchronized void _scan() {
        var beanPairs = iBeanScanner.getBeansWithAnnotationAndInterface(TeaNekoClient.class, ITeaNekoClient.class);
        for (var pair : beanPairs.values()) {
            var beanAnnotation = pair.first();
            if(!beanAnnotation.enabled()) {
                continue;
            }
            handlerMap.put(beanAnnotation.value(), pair);
        }
    }
}
