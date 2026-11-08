package org.zexnocs.teanekoplugin.agent.expression.expression.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionClientRegistry;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionRuntime;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyExtensionRegistry;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentExpressionFileConfig;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 组装声明式表达能力与原生目标队列，构造期不读取业务配置。
 * <br>2026/11/03：扫描明确客户端图片与原生表情编码端口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods=false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
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
     * @param components 当前有效组件参数
     * @param mapper 转换器
     * @param executor 原业务执行器
     * @return 表达编译器
     */
    @Bean public ExpressionRuntime expressionRuntime(ReplyExtensionRegistry registry, IFileConfigService files, AgentComponentAccess components, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor){
        return new ExpressionRuntime(registry,()->files.requireCurrent(AgentExpressionFileConfig.class).snapshot(),
                scope->components.settings(scope, "expression").map(parameters -> parameters.path("expressionEnabled").asBoolean(true)).orElse(false),mapper,executor);
    }
    /**
     * 2026/11/07：业务停用时恢复宿主默认队列，队列本身不随表达插件销毁。
     * @param runtime 表达配置来源
     * @param plugins 当前业务资格
     * @return 动态有界队列策略
     */
    @Bean
    public org.zexnocs.teanekoagent.runtime.delivery.IAgentReplyQueuePolicy expressionQueuePolicy(
            ExpressionRuntime runtime, PluginRegistry plugins) {
        return () -> plugins.isEnabled("agent.expression") ? runtime.settings()
                : org.zexnocs.teanekoagent.runtime.delivery.AgentReplyQueueLimits.defaults();
    }
}
