package org.zexnocs.teanekocore.actuator.task;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.api.TaskStage;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.exception.InterfaceAndAnnotationInconsistencyException;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扫描任务阶段和其命名空间，并注册到 scanner 的容器中。
 * <br>2026/11/02：同优先级阶段按 Bean 名稳定排序。
 *
 * @author zExNocs
 * @date 2026/02/12
 * @since 4.0.2
 * @version 4.6.0-l
 * @see TaskStage
 * @see ITaskStage
 */
@Service
public class TaskStageScanner extends AbstractScanner {

    /// 命名空间 → 任务阶段列表。
    private final Map<String, List<TaskStageWrapper>> taskStages = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;

    /**
     * 2026/11/02：补充扫描及诊断依赖合同。
     * @param iBeanScanner 原 Bean 扫描器
     * @param iLogger 项目日志
     */
    public TaskStageScanner(IBeanScanner iBeanScanner, ILogger iLogger) {
        this.iBeanScanner = iBeanScanner;
        this.iLogger = iLogger;
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
        var taskStageList = taskStages.get(namespace);
        // 如果不存在，返回空列表
        if (taskStageList == null) {
            return List.of();
        }
        return taskStageList.stream()
                .map(TaskStageWrapper::taskStage)
                .toList();
    }

    /** 2026/11/02：扫描阶段归属并生成稳定顺序，不执行阶段业务。 */
    @Override
    protected synchronized void _scan() {
        // 获取所有带有TaskStage注解的Bean
        Map<String, Pair<TaskStage, ITaskStage>> taskStageBeans;
        try {
            taskStageBeans = iBeanScanner.getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class);
        } catch (InterfaceAndAnnotationInconsistencyException e) {
            iLogger.errorWithReport(TaskStageScanner.class.getSimpleName(),
                    "扫描任务阶段失败，接口和注解标注的 Bean 不一", e);
            return;
        }
        // 将所有扫描出来的任务阶段类加入到命名空间对应的列表中
        taskStageBeans.forEach((beanName, pair) -> {
            var annotation = pair.first();
            var taskStage = pair.second();
            // 获取命名空间
            var namespaces = annotation.value();
            int priority = annotation.priority();
            // 将任务阶段类加入到命名空间对应的列表中
            for (var namespace: namespaces) {
                var taskStageList = taskStages.computeIfAbsent(namespace,
                        k -> new CopyOnWriteArrayList<>());
                taskStageList.add(new TaskStageWrapper(taskStage, priority, beanName));
            }
        });

        // 根据优先级排序
        taskStages.values().forEach(list -> list.sort(TaskStageWrapper::compareTo));
    }

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        taskStages.clear();
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
