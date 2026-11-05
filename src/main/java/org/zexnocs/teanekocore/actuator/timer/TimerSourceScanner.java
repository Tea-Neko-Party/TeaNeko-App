package org.zexnocs.teanekocore.actuator.timer;

import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerViewSource;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.HashSet;
import java.util.List;

/**
 * 低频发现调度观测来源和任务持有者，页面查询只遍历缓存实例。
 * 缓存不包含任务快照，任务注册、移除和执行状态仍从原持有者实时读取。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Component
public final class TimerSourceScanner implements IScanner {
    private final IBeanScanner scanner;
    private volatile Sources sources = new Sources(List.of(), List.of());
    private boolean initialized;

    /** @param scanner 原 Bean 发现入口，构造时不实例化调度来源 */
    public TimerSourceScanner(IBeanScanner scanner) {
        this.scanner = scanner;
    }

    /** 在原重载服务初始化阶段发现来源一次，不由页面查询触发。 */
    @Override
    public synchronized void init() {
        if (!initialized) {
            reload();
        }
    }

    /**
     * 显式重扫并校验来源唯一性，失败时保留完整旧目录。
     *
     * @throws IllegalArgumentException 当来源标识非法或重复时
     */
    @Override
    public synchronized void reload() {
        var views = List.copyOf(scanner.getBeansOfType(ITimerViewSource.class).values());
        var holders = List.copyOf(scanner.getBeansOfType(ScheduledTaskHolder.class).values());
        var ids = new HashSet<String>();
        for (var view : views) {
            String id = view.source();
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}") || id.equals("core") || !ids.add(id)) {
                throw new IllegalArgumentException("定时观测来源标识无效或重复。");
            }
        }
        sources = new Sources(views, holders);
        initialized = true;
    }

    /** @return 当前已发现观测来源，不扫描 Bean */
    public List<ITimerViewSource> views() {
        return sources.views();
    }

    /** @return 原任务持有者，调用方实时读取其任务，不缓存任务状态 */
    public List<ScheduledTaskHolder> holders() {
        return sources.holders();
    }

    /**
     * 一次原子发布的发现结果。
     *
     * @param views 观测来源
     * @param holders 任务持有者
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.2-l
     * @version 4.8.2-l
     */
    private record Sources(List<ITimerViewSource> views, List<ScheduledTaskHolder> holders) {}
}
