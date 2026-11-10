package org.zexnocs.teanekoplugin.agent.expression.sticker.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerLifecycle;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAccess;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerCollectionService;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerExtensionRegistry;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerLibrary;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 组合实例共享图库与独立后台采集；配置、文件、模型均在实际任务才读取。
 * <br>2026/11/03：有事件适配器时接入受限采集和真实提交观察。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：采集资格复用客户端目录，实时检查自身份而不重复发现 Bean。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 * <br>2026/11/10：区分聊天采集资格与客户端就绪，旧排队任务允许等待连接。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.1-l
 */
@Configuration(proxyBeanMethods=false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public class AgentStickerConfig {
    /**
     * @param scanner 原扫描器
     * @param plugins 插件目录
     * @return 固定扩展目录
     */
    @Bean public StickerExtensionRegistry stickerExtensionRegistry(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins){return new StickerExtensionRegistry(scanner,plugins.getIfAvailable());}
    /**
     * 2026/11/05：每次采集校验仅查询客户端缓存，不执行底层扫描。
     * @param registry 扩展目录
     * @param files 本地配置
     * @param components 当前有效组件参数
     * @param clients 当前客户端缓存目录
     * @param lifecycle 可选事件适配
     * @return 素材领域服务
     */
    @Bean
    public StickerLibrary stickerLibrary(StickerExtensionRegistry registry, IFileConfigService files,
            AgentComponentAccess components, TeaNekoClientScanner clients,
            ObjectProvider<IStickerLifecycle> lifecycle) {
        // 2026/11/10：组件关闭是明确撤销；账号未就绪允许未开始的持久作业继续等待。
        return new StickerLibrary(registry, () -> files.requireCurrent(AgentStickerFileConfig.class).snapshot(), source -> {
            boolean enabled = components.settings(source.scope(), "sticker")
                    .map(value -> value.path("stickerCollectEnabled").asBoolean(true)).orElse(false);
            if (!enabled) {
                return StickerAccess.SOURCE_DISABLED;
            }
            boolean connected = clients.getHandlerMap().values().stream().map(IPair::second)
                    .filter(client -> client.getClientId().equals(source.scope().clientId()))
                    .anyMatch(client -> client.getTeaNekoToolbox().getSelfPlatformId()
                            .filter(source.self()::equals).isPresent());
            return connected ? StickerAccess.ALLOWED : StickerAccess.CLIENT_UNAVAILABLE;
        }, Clock.systemUTC(), lifecycle.getIfAvailable());
    }
    /**
     * @param library 目录
     * @param registry 扩展
     * @param executor 原业务域
     * @param scheduler 原调度器
     * @param logger 原日志
     * @return 自有后台任务服务
     */
    @Bean(destroyMethod="close") public StickerCollectionService stickerCollectionService(StickerLibrary library, StickerExtensionRegistry registry,
                                                                                          @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger){return new StickerCollectionService(library,registry,executor,scheduler,logger);}
}
