package org.zexnocs.teanekocore.actuator.task;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存低频注册确认的任务归属，并为每个真实任务固定插件调用代次。
 * <br>2026/11/07：排队、重试和外部结果沿用同一令牌，不在执行路径发现注解。
 * <br>2026/11/07：原任务物理完成前保持统一插件占用，第三方定时任务同样参与停用排空。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class TaskPluginBindings {
    private final PluginRegistry plugins;
    private final Map<ITaskConfig<?>, Binding> owners = new IdentityHashMap<>();
    private final Map<UUID, PluginRegistry.Token> tokens = new ConcurrentHashMap<>();

    /**
     * 借用当前插件注册表。
     * @param plugins 统一插件状态与代次来源
     */
    public TaskPluginBindings(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /**
     * 注册已由宿主解析的归属，相同任务配置不能归属不同插件。
     * @param config 原任务配置，按对象身份定位
     * @param pluginId 已确认归属，宿主任务为空
     * @throws IllegalArgumentException 当同一配置归属冲突时
     */
    public synchronized void bind(ITaskConfig<?> config, @Nullable String pluginId) {
        if (pluginId == null) {
            return;
        }
        plugins.requireRegistered(pluginId);
        var previous = owners.get(config);
        if (previous != null && !previous.pluginId().equals(pluginId)) {
            throw new IllegalArgumentException("同一任务配置不能归属多个插件。");
        }
        owners.put(config, new Binding(pluginId, previous == null ? 1 : previous.references() + 1));
    }

    /**
     * 撤销一个注册来源；已经排队的任务继续保留自己的令牌。
     * @param config 被移除定时器的原任务配置
     */
    public synchronized void unbind(ITaskConfig<?> config) {
        var previous = owners.get(config);
        if (previous != null) {
            if (previous.references() == 1) {
                owners.remove(config);
            } else {
                owners.put(config, new Binding(previous.pluginId(), previous.references() - 1));
            }
        }
    }

    /**
     * 读取已登记归属，不解析类或方法。
     * @param config 原任务配置
     * @return 插件标识，未归属宿主任务为空
     */
    public synchronized @Nullable String owner(ITaskConfig<?> config) {
        var binding = owners.get(config);
        return binding == null ? null : binding.pluginId();
    }

    /**
     * 查询插件当前有效状态。
     * @param pluginId 已缓存归属，宿主任务为空
     * @return 可被发现和触发时为真
     */
    public boolean enabled(@Nullable String pluginId) {
        return pluginId == null || plugins.isEnabled(pluginId);
    }

    /**
     * 2026/11/07：首次排队固定本任务代次与占用，重试不取得新的插件资格。
     * @param task 当前真实任务
     * @throws TaskNoRetryRuntimeException 当插件不再接受本任务时
     */
    public void capture(ITask<?> task) {
        try {
            String owner = owner(task.getConfig());
            if (owner != null && !tokens.containsKey(task.getKey())) {
                var call = plugins.open(owner);
                var token = call.token();
                if (tokens.putIfAbsent(task.getKey(), token) == null) {
                    call.track(task.getExecution().completion().whenComplete((ignored, failure) ->
                            tokens.remove(task.getKey(), token)));
                } else {
                    call.close();
                }
            }
            check(task);
        } catch (IllegalStateException failure) {
            throw new TaskNoRetryRuntimeException("任务所属插件已停用或调用版本失效。", failure);
        }
    }

    /**
     * 执行和结果交付前复核原代次，不因重新启用恢复旧调用。
     * @param task 当前真实任务
     * @throws TaskNoRetryRuntimeException 当原调用代次已经失效时
     */
    public void check(ITask<?> task) {
        var token = tokens.get(task.getKey());
        if (token != null) {
            try {
                plugins.check(token);
            } catch (IllegalStateException failure) {
                throw new TaskNoRetryRuntimeException("任务插件调用版本已失效，已发生的业务操作不会回滚。", failure);
            }
        }
    }

    /**
     * 同一配置被多个实际定时器引用时保留其唯一归属。
     * @param pluginId 稳定归属
     * @param references 当前注册计数
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Binding(String pluginId, int references) {}
}
