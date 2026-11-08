package org.zexnocs.teanekoplugin.web.common;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoclient.web.IWebRoomPreparation;
import org.zexnocs.teanekoclient.web.WebChat;
import org.zexnocs.teanekoclient.web.WebRoomPreparation;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 为首次进入的网页私聊提供启用的 Agent 初值，不覆盖已有记录或其他客户端配置。
 *
 * 修改记录：<br>
 * 2026/11/04：业务标识与 Spring Bean 名解耦，使用独立 id 声明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
// 2026/11/04：保持原业务标识，容器按实现类生成 Bean 名。
@WebRoomPreparation(id = "agent.private-default")
@PluginOwned("web.common")
public final class WebPrivateAgentPreparation implements IWebRoomPreparation {
    private final ConfigEditService configs;

    /** @param configs 原配置事务入口，使用管理器声明的 Agent 专用表 */
    public WebPrivateAgentPreparation(ConfigEditService configs) {
        this.configs = configs;
    }

    /**
     * @param room 服务端固定的真实房间
     * @param guard 当前会话及插件权限
     * @return 未登记私聊初始化完成；其他房间或已登记记录保持原值
     */
    @Override
    public CompletionStage<Void> prepare(WebChat.Room room, Runnable guard) {
        guard.run();
        if (!room.kind().equals("PRIVATE")) {
            return CompletableFuture.completedStage(null);
        }
        var initial = new AgentScopeConfigData();
        initial.setEnabled(true);
        var manager = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
        return configs.initializeIfAbsent(manager, room.scope(), initial, guard).thenAccept(ignored -> {});
    }
}
