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
 *
 * @see TaskStage
 * @see ITaskStage
 * @author zExNocs
 * @date 2026/02/12
 */
@Service
public class TaskStageScanner extends AbstractScanner {

    /// 命名空间 → 任务阶段列表。
    private final Map<String, List<TaskStageWrapper>> taskStages = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;

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

    @Override
    protected synchronized void _scan() {
        // 清理之前的扫描结果
        taskStages.clear();
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
        taskStageBeans.values().forEach(pair -> {
            var annotation = pair.first();
            var taskStage = pair.second();
            // 获取命名空间
            var namespaces = annotation.value();
            int priority = annotation.priority();
            // 将任务阶段类加入到命名空间对应的列表中
            for (var namespace: namespaces) {
                var taskStageList = taskStages.computeIfAbsent(namespace,
                        k -> new CopyOnWriteArrayList<>());
                taskStageList.add(new TaskStageWrapper(taskStage, priority));
            }
        });

        // 根据优先级排序
        taskStages.values().forEach(list -> list.sort(TaskStageWrapper::compareTo));
    }

    /**
     * 包装类，用于比较任务阶段的优先级。
     */
    private record TaskStageWrapper(ITaskStage taskStage, int priority) implements Comparable<TaskStageWrapper> {
        @Override
        public int compareTo(TaskStageWrapper o) {
            // 优先级大的排在前面
            return Integer.compare(o.priority(), priority());
        }
    }
}
