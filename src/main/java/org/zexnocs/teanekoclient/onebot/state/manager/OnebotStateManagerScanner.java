package org.zexnocs.teanekoclient.onebot.state.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoclient.onebot.state.OnebotState;
import org.zexnocs.teanekoclient.onebot.state.manager.interfaces.IOnebotStateManager;
import org.zexnocs.teanekoclient.onebot.state.manager.interfaces.OnebotStateManager;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * onebot state manager 扫描器。
 * <br>扫描所有实现了接口 {@link IOnebotStateManager}
 * 并加上注解 {@link OnebotStateManager}
 * 的 bean
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@Component
@RequiredArgsConstructor
public class OnebotStateManagerScanner extends AbstractScanner {

    /// state → manager 映射
    private final Map<OnebotState, IOnebotStateManager> map = new ConcurrentHashMap<>();

    /// bean scanner
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        map.clear();
    }

    /**
     * 扫描方法。
     *
     */
    @Override
    protected void _scan() {
        var pairs = iBeanScanner.getBeansWithAnnotationAndInterface(OnebotStateManager.class, IOnebotStateManager.class);
        for(var pair : pairs.values()){
            var states = pair.first().value();
            var bean = pair.second();
            for(var state : states) {
                if(map.putIfAbsent(state, bean) != null) {
                    iLogger.warn(this.getClass().getSimpleName(), """
                                    有多个状态管理器处理了同一个状态：
                                    1. %s
                                    2. %s
                                    只启用了第 1 个管理器。""".formatted(
                                            map.get(state).getClass().getName(),
                                            bean.getClass().getName()));
                }
            }
        }
    }

    /**
     * 根据状态获取管理器
     *
     * @param state 状态
     */
    public IOnebotStateManager get(OnebotState state) {
        return map.get(state);
    }
}
