package org.zexnocs.teanekoagent.component.llm;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.route.ILLMRouteSource;
import org.zexnocs.teanekoagent.llm.framework.route.LLMRouteSource;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * 当前聊天的私有模型来源；读取 Agent 专用 cfg DTO，不把凭据复制到模型文件或进程缓存。
 * 路由绑定装配摘要，配置变更立即使原路由失效；行锁只覆盖同步请求构造，不等待 HTTP。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/10：公开可选择模型的字段契约及安全配置投影。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.1-l
 */
@LLMRouteSource(namespace = "agent")
public final class ScopedLLMRouteSource implements ILLMRouteSource {
    private final ConfigDataRegisterService registration;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final LLMComponent component;
    // 2026/11/10：按已发布组件目录复核扩展模型字段，读取时不扫描。
    private final org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoagent.component.AgentComponentRegistry> components;
    private static final ConfigManager MANAGER = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);

    /**
     * @param registration 专用配置 DTO
     * @param mapper 原转换器
     * @param transactions 原数据库事务管理器
     * @param component 私有模型参数转换及环境变量解析入口
     * @param components 延迟读取已发布组件目录，避免初始化循环
     */
    public ScopedLLMRouteSource(ConfigDataRegisterService registration, ObjectMapper mapper,
                               PlatformTransactionManager transactions, LLMComponent component,
            org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoagent.component.AgentComponentRegistry> components) {
        this.registration = registration;
        this.mapper = mapper;
        this.component = component;
        this.components = components;
        transaction = new TransactionTemplate(transactions);
    }

    /**
     * @param scope 当前可信配置键
     * @param assembly 固定装配
     * @return 不含凭据的版本化内部路由
     */
    public static String route(String scope, AgentAssembly assembly) {
        return "agent:" + HexFormat.of().formatHex(scope.getBytes(StandardCharsets.UTF_8)) + ":" + assembly.revision();
    }

    /**
     * 2026/11/10：扩展组件的私有模型沿用原配置行锁及装配版本绑定。
     * @param scope 可信聊天配置键
     * @param assembly 固定装配
     * @param component 声明模型字段的组件
     * @param field 声明为 MODEL 的字段
     * @return 不含凭据的内部路由
     */
    public static String route(String scope, AgentAssembly assembly, String component, String field) {
        return route(scope, assembly) + ":" + component + ":" + field;
    }

    /**
     * @param route 固定内部路由
     * @return 经过当前登记、开关及版本复核的原配置
     */
    private LLMModelFileConfigParameter entry(String route) {
        // 2026/11/10：扩展位置必须来自已注册组件声明，不能以任意 JSON 指针读取秘密。
        if (!route.matches("agent:[0-9a-f]{2,2048}:[0-9a-f]{64}(:[a-z][a-z0-9.-]{0,63}:[a-zA-Z][a-zA-Z0-9]{0,63})?")) {
            throw new LLMConfigurationException("聊天模型路由无效。");
        }
        var parts = route.split(":");
        var key = new String(HexFormat.of().parseHex(parts[1]), StandardCharsets.UTF_8);
        var row = registration.getDto(MANAGER).atomic().find(key, EasyDataLock.READ);
        if (row == null) {
            throw new LLMConfigurationException("聊天模型配置已注销。");
        }
        var config = mapper.readValue(row.getValue(), AgentScopeConfigData.class);
        var serialized = config.getComponents();
        if (!config.isEnabled() || serialized == null || !config.assembly().revision().equals(parts[2])) {
            throw new LLMConfigurationException("聊天模型配置已修改或关闭，请重新发起请求。");
        }
        var parameters = AgentAssembly.document(serialized).path("components").path("llm");
        if (parts.length == 5) {
            var registry = components.getObject();
            boolean declared = registry.describe().stream().filter(item -> item.id().equals(parts[3]))
                    .flatMap(item -> item.fields().stream())
                    .anyMatch(field -> field.name().equals(parts[4]) && field.kind().equals("MODEL"));
            if (!declared || !registry.effective(serialized).has(parts[3])) {
                throw new LLMConfigurationException("模型字段所属组件已经停用或不可用。");
            }
            parameters = AgentAssembly.document(serialized).path("components").path(parts[3]).path(parts[4]);
        }
        if (!parameters.path("source").asString().equals("custom")) {
            throw new LLMConfigurationException("当前聊天未安装私有模型。");
        }
        return component.resolveConfiguration(parameters);
    }

    /**
     * @param route 私有路由
     * @return 已声明协议
     */
    @Override
    public LLMModelId adapter(String route) {
        return fixed(route, () -> entry(route).adapterId());
    }

    /**
     * @param route 私有路由
     * @param base 协议初值
     * @return 不含连接信息的模型参数
     */
    @Override
    public LLMModelOptions defaults(String route, ILLMModelOptions base) {
        return fixed(route, () -> {
            var config = entry(route);
            if (!base.findProvider().orElseThrow().equals(config.adapterId().id())) {
                throw new LLMConfigurationException("私有模型与实际协议不一致。");
            }
            var options = config.toOptions(base);
            options.setProvider(route);
            return options;
        });
    }

    /**
     * @param route 私有路由
     * @return 仅供实际认证使用的连接
     */
    @Override
    public LLMProviderAccess credentials(String route) {
        return fixed(route, () -> {
            var config = entry(route);
            return new LLMProviderAccess(config.getApiKey(), config.getBaseUrl(), config.getApiPath());
        });
    }

    /**
     * @param route 私有路由
     * @param action 同步构造
     * @param <T> 结果类型
     * @return 原配置行读锁内的结果
     */
    @Override
    public <T> T fixed(String route, Supplier<T> action) {
        return transaction.execute(status -> {
            entry(route);
            return action.get();
        });
    }

    /** @param options 原解析参数，版本已经固定在路由中 */
    @Override
    public void verify(ILLMModelOptions options) {
        fixed(options.findProvider().orElseThrow(), () -> Boolean.TRUE);
    }

    /**
     * @param options 当前参数
     * @return 已经由路由固定配置版本的参数
     */
    @Override
    public LLMModelOptions stamp(LLMModelOptions options) {
        verify(options);
        return options;
    }
}
