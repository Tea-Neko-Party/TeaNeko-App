package org.zexnocs.teanekocore.actuator.task;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.api.TaskStage;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.exception.InterfaceAndAnnotationInconsistencyException;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 扫描任务阶段和其命名空间，并注册到 scanner 的容器中。
 * <br>2026/11/02：同优先级阶段按 Bean 名稳定排序。
 * <br>2026/11/07：初始化冻结阶段插件归属，完整重扫成功后原子发布缓存。
 *
 * @author zExNocs
 * @date 2026/02/12
 * @since 4.0.2
 * @version 4.9.0-l
 * @see TaskStage
 * @see ITaskStage
 */
@Service
public class TaskStageScanner implements IScanner {

    /// 命名空间 → 任务阶段列表。
    private volatile @Nullable Map<String, List<TaskStageWrapper>> taskStages;
    private final IBeanScanner iBeanScanner;

    /**
     * 2026/11/02：补充扫描及诊断依赖合同。
     * @param iBeanScanner 原 Bean 扫描器
     * @param iLogger 项目日志
     */
    public TaskStageScanner(IBeanScanner iBeanScanner, ILogger iLogger) {
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 获取命名空间对应的任务阶段列表。
     * @param namespace 命名空间。
     * @return 任务阶段列表。
     */
    @NonNull
    public List<ITaskStage> getTaskStages(@Nullable String namespace) {
        // 如果 namespace 为 null，返回空列表
        if (namespace == null) {
            return List.of();
        }
        var snapshot = taskStages;
        if (snapshot == null) {
            throw new IllegalStateException("任务阶段目录尚未初始化，请先完成初始化或显式重扫。");
        }
        var taskStageList = snapshot.get(namespace);
        // 如果不存在，返回空列表
        if (taskStageList == null) {
            return List.of();
        }
        return taskStageList.stream()
                .map(TaskStageWrapper::taskStage)
                .toList();
    }

    /** 2026/11/07：首次完整发现成功后发布目录，失败仍保持未就绪。 */
    @Override
    public synchronized void init() {
        if (taskStages == null) {
            reload();
        }
    }

    /** 2026/11/07：冻结归属并原子发布完整阶段目录，失败不破坏旧快照。 */
    @Override
    public synchronized void reload() {
        Map<String, IPair<TaskStage, ITaskStage>> taskStageBeans;
        try {
            taskStageBeans = iBeanScanner.getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class);
        } catch (InterfaceAndAnnotationInconsistencyException failure) {
            throw new IllegalStateException("任务阶段注解与接口不一致，保留原目录。", failure);
        }
        var discovered = new HashMap<String, List<TaskStageWrapper>>();
        taskStageBeans.forEach((beanName, pair) -> {
            var annotation = pair.first();
            var compiled = TaskStageBinding.of(pair.second());
            for (var namespace : annotation.value()) {
                discovered.computeIfAbsent(namespace, ignored -> new ArrayList<>())
                        .add(new TaskStageWrapper(compiled, annotation.priority(), beanName));
            }
        });
        var ready = new HashMap<String, List<TaskStageWrapper>>();
        discovered.forEach((namespace, stages) -> ready.put(namespace, stages.stream().sorted().toList()));
        taskStages = Map.copyOf(ready);
    }

    /**
     * 包装类，用于比较任务阶段的优先级。
     * <br>2026/11/02：相同优先级比较稳定注册名。
     * @param taskStage 阶段实现
     * @param priority 优先级
     * @param beanName 注册名
     * @author zExNocs
     * @date 2026/02/12
     * @since 4.0.2
     * @version 4.6.0-l
     */
    private record TaskStageWrapper(ITaskStage taskStage, int priority, String beanName) implements Comparable<TaskStageWrapper> {
        /**
         * 按优先级降序、注册名升序排序。
         * @param o 另一阶段
         * @return 排序结果
         */
        @Override
        public int compareTo(TaskStageWrapper o) {
            // 2026/11/02：不依赖容器 Map 的迭代顺序。
            int result = Integer.compare(o.priority(), priority());
            return result != 0 ? result : beanName.compareTo(o.beanName);
        }
    }
}
