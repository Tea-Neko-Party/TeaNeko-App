package org.zexnocs.teanekocore.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import java.util.concurrent.CompletionStage;

/**
 * 已由 Spring 装配的插件资源生命周期；停用可恢复，最终销毁仍由资源所有者负责。
 * <br>2026/11/07：启动完成后才发布调用资格，停用阶段必须代表自有资源的真实终态。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IPluginLifecycle {
    /**
     * 返回已经注册的所属插件。
     * @return 插件稳定标识
     */
    String pluginId();

    /**
     * 返回插件内部唯一的生命周期标识。
     * @return 稳定局部标识
     */
    String id();

    /**
     * 创建本轮启用拥有的资源，不重放过期业务或重复登记任务。
     * @return 所有启动工作完成的阶段，失败仍允许 stop 清理部分资源
     */
    CompletionStage<Void> start();

    /**
     * 停止新增任务并等待自有工作真实结束；宿主已先撤销新调用资格。
     * @return 所有停用清理完成的阶段，不能提前释放仍在使用的资源
     */
    CompletionStage<Void> stop();
}
