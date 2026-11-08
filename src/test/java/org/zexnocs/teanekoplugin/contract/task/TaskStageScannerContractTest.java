package org.zexnocs.teanekoplugin.contract.task;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：插件归属夹具遵循与生产一致的业务包边界。
import org.zexnocs.teanekocore.actuator.task.*;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.api.SetTaskStage;
import org.zexnocs.teanekocore.actuator.task.api.TaskStage;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.utils.scanner.exception.InterfaceAndAnnotationInconsistencyException;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 任务阶段归属发现的初始化、失败恢复与配置创建回归。
 * <br>2026/11/07：验证查询不懒扫描、重扫失败保留快照以及方法归属优先级。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class TaskStageScannerContractTest {
    /** 初始化失败不隐式恢复，成功快照在后续失败重扫中保持可用。 */
    @Test
    void queriesNeverDiscoverAndFailedReloadRetainsCompleteOwnership() {
        var beans = mock(IBeanScanner.class);
        var scanner = new TaskStageScanner(beans, mock(ILogger.class));
        assertThrows(IllegalStateException.class, () -> scanner.getTaskStages("test"));
        verifyNoInteractions(beans);
        var stage = new OwnedStage();
        Map<String, IPair<TaskStage, ITaskStage>> found = Map.of("owned",
                new IndependentPair<>(OwnedStage.class.getAnnotation(TaskStage.class), stage));
        when(beans.getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class)).thenReturn(found);
        scanner.init();
        var first = scanner.getTaskStages("test");
        assertEquals("test.method", assertInstanceOf(TaskStageBinding.class, first.getFirst()).pluginId());
        assertEquals(first, scanner.getTaskStages("test"));
        verify(beans, times(1)).getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class);
        when(beans.getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class))
                .thenThrow(new InterfaceAndAnnotationInconsistencyException("测试阶段声明不完整。"));
        assertThrows(IllegalStateException.class, scanner::reload);
        assertEquals(first, scanner.getTaskStages("test"));
        verify(beans, times(2)).getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class);
    }

    /** 手动配置与命名空间方法声明在创建时固定，后续执行只需读取配置。 */
    @Test
    void explicitConfigurationFreezesManualStagesAndCallableNamespace() {
        var config = TaskConfig.<Void>builder().name("显式阶段")
                .taskStageNamespace("fallback").callable(this::configuredTask)
                .taskStages(List.of(new OwnedStage())).build();
        assertEquals("test", config.getTaskStageNamespace());
        assertNotNull(config.getTaskStages());
        assertEquals("test.method", assertInstanceOf(TaskStageBinding.class, config.getTaskStages().getFirst()).pluginId());
        config.setTaskStages(null);
        assertNull(config.getTaskStages());
        config.setTaskStages(List.of(new OwnedStage()));
        assertEquals("test.method", assertInstanceOf(TaskStageBinding.class, config.getTaskStages().getFirst()).pluginId());
    }

    /** @return 用于验证声明提取的无副作用结果。 */
    @SetTaskStage("test")
    private ITaskResult<Void> configuredTask() {
        return EmptyTaskResult.INSTANCE;
    }

    /**
     * 方法归属覆盖类型归属的阶段夹具。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @TaskStage("test")
    @PluginOwned("test.type")
    private static final class OwnedStage implements ITaskStage {
        /**
         * 保留方法级归属而不触发真实业务。
         * @param chain 原任务链
         * @return 空结果
         */
        @Override
        @PluginOwned("test.method")
        public ITaskResult<?> process(TaskStageChain chain) {
            return EmptyTaskResult.INSTANCE;
        }
    }
}
