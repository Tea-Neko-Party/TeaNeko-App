package org.zexnocs.teanekoagent.component.sticker.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.component.sticker.IStickerLifecycle;
import org.zexnocs.teanekoagent.component.sticker.StickerCollectionService;
import org.zexnocs.teanekoagent.component.sticker.StickerExtensionRegistry;
import org.zexnocs.teanekoagent.component.sticker.StickerLibrary;
import org.zexnocs.teanekoagent.component.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 组合实例共享图库与独立后台采集；配置、文件、模型均在实际任务才读取。
 * <br>2026/11/03：有事件适配器时接入受限采集和真实提交观察。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Configuration(proxyBeanMethods=false)
public class AgentStickerConfig {
    /**
     * @param scanner 原扫描器
     * @param plugins 插件目录
     * @return 固定扩展目录
     */
    @Bean public StickerExtensionRegistry stickerExtensionRegistry(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins){return new StickerExtensionRegistry(scanner,plugins.getIfAvailable());}
    /**
     * @param registry 扩展目录
     * @param files 本地配置
     * @param configs 登记数据
     * @param manager 原声明
     * @param scanner 当前客户端
     * @param lifecycle 可选事件适配
     * @return 素材领域服务
     */
    @Bean public StickerLibrary stickerLibrary(StickerExtensionRegistry registry, IFileConfigService files, IConfigDataService configs, AgentScopeConfigManager manager, IBeanScanner scanner, ObjectProvider<IStickerLifecycle> lifecycle){
        return new StickerLibrary(registry,()->files.requireCurrent(AgentStickerFileConfig.class).snapshot(),source->{
            boolean enabled=configs.getConfigData(manager,AgentScopeConfigData.class,source.scope().scopeId()).map(v->v.isEnabled()&&v.isStickerCollectEnabled()).orElse(false);
            return enabled&&scanner.getBeansOfType(ITeaNekoClient.class).values().stream().filter(v->v.getClientId().equals(source.scope().clientId()))
                    .anyMatch(v->v.getTeaNekoToolbox().getSelfPlatformId().filter(source.self()::equals).isPresent());
        // 2026/11/03：旧独立容器可以不装配事件，默认应用复用原 Core 总线。
        },Clock.systemUTC(),lifecycle.getIfAvailable());
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
