package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.expression.ExpressionClientRegistry;
import org.zexnocs.teanekoagent.expression.ExpressionRuntime;
import org.zexnocs.teanekoagent.expression.ReplyCoordinator;
import org.zexnocs.teanekoagent.expression.ReplyExtensionRegistry;
import org.zexnocs.teanekoagent.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 组装声明式表达能力与原生目标队列，构造期不读取业务配置。
 * <br>2026/11/03：扫描明确客户端图片与原生表情编码端口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Configuration(proxyBeanMethods=false)
public class AgentExpressionConfig {
    /**
     * 2026/11/03：未知客户端保持无媒体能力，新增实现不修改中央分支。
     * @param scanner 原扫描器
     * @param plugins 原插件目录
     * @return 客户端能力目录
     */
    @Bean public ExpressionClientRegistry expressionClientRegistry(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins){return new ExpressionClientRegistry(scanner,plugins.getIfAvailable());}
    /**
     * 扫描原容器实现。
     * @param scanner 扫描器
     * @param plugins 插件目录
     * @return 扩展目录
     */
    @Bean public ReplyExtensionRegistry replyExtensionRegistry(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins){return new ReplyExtensionRegistry(scanner,plugins.getIfAvailable());}
    /**
     * 使用原 scope 登记和本地文件。
     * @param registry 扩展目录
     * @param files 文件服务
     * @param configs 作用域配置
     * @param manager 原声明
     * @param mapper 转换器
     * @param executor 原业务执行器
     * @return 表达编译器
     */
    @Bean public ExpressionRuntime expressionRuntime(ReplyExtensionRegistry registry, IFileConfigService files, IConfigDataService configs, AgentScopeConfigManager manager, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor){
        return new ExpressionRuntime(registry,()->files.requireCurrent(AgentExpressionFileConfig.class).snapshot(),
                scope->configs.getConfigData(manager,AgentScopeConfigData.class,scope.scopeId()).map(v->v.isEnabled()&&v.isExpressionEnabled()).orElse(false),mapper,executor);
    }
    /**
     * 复用单个有界队列服务。
     * @param executor 业务执行器
     * @param scheduler 原调度器
     * @param runtime 表达配置
     * @return 队列拥有者
     */
    @Bean(destroyMethod="close") public ReplyCoordinator replyCoordinator(@Qualifier("virtualExecutor") ExecutorService executor,@Qualifier("taskExecutor") ScheduledExecutorService scheduler,ExpressionRuntime runtime){
        return new ReplyCoordinator(executor,scheduler,Clock.systemUTC(),runtime::settings);
    }
}
