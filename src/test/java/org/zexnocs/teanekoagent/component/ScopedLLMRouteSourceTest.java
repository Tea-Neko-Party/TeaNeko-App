package org.zexnocs.teanekoagent.component;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.component.llm.ScopedLLMRouteSource;
import org.zexnocs.teanekoagent.component.llm.configuration.FileLLMCredentialResolver;
import org.zexnocs.teanekoagent.component.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.component.llm.framework.route.ILLMRouteSource;
import org.zexnocs.teanekoagent.component.llm.framework.route.LLMRouteDirectory;
import org.zexnocs.teanekoagent.component.llm.framework.route.LLMRouteSource;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 2026/11/05：跟随模型配置装配迁移更新凭据解析器引用。

/**
 * 私有模型使用原 DTO、配置来源及凭据边界，不依赖系统模型文件，不请求真实网络。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：显式初始化来源目录，模型查询不触发 Bean 发现。
 *
 * <br>2026/11/05：适配模型专属 configuration 包，保留原私有路由隔离验证。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.2-l
 */
class ScopedLLMRouteSourceTest {
    /** 验证专有路由的实际协议、独立凭据与配置撤销，秘密不进入普通模型选项。 */
    @Test
    void resolvesPrivateModelAndRevokesOldRouteAfterConfigurationChange() {
        var json = new ObjectMapper();
        var registration = mock(ConfigDataRegisterService.class);
        var dto = mock(IEasyDataDto.class);
        var atomic = mock(IEasyDataTransactionDto.class);
        var row = mock(EasyDataRow.class);
        when(registration.getDto(any())).thenReturn(dto);
        when(dto.atomic()).thenReturn(atomic);
        when(atomic.find("private@test", EasyDataLock.READ)).thenReturn(row);
        String serialized = AgentAssemblyTest.privateAssembly("private-secret");
        when(row.getValue()).thenReturn(json.writeValueAsString(Map.of("enabled", true, "components", serialized)));
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        var source = new ScopedLLMRouteSource(registration, json, transactions, new LLMComponent(new YAMLFileTypeParser()));
        String route = ScopedLLMRouteSource.route("private@test", AgentAssembly.parse(serialized));
        var scanner = mock(IBeanScanner.class);
        when(scanner.getBeansWithAnnotationAndInterface(LLMRouteSource.class, ILLMRouteSource.class))
                .thenReturn(Map.of("private", new IndependentPair<>(ScopedLLMRouteSource.class.getAnnotation(LLMRouteSource.class), source)));
        doReturn(ScopedLLMRouteSource.class).when(scanner).getBeanClass(source);
        var routes = new LLMRouteDirectory(scanner);
        // 2026/11/05：模拟原重载服务的低频初始化，后续请求只读来源缓存。
        routes.init();
        var files = mock(IFileConfigService.class);
        var credentials = new FileLLMCredentialResolver(files);
        credentials.installRoutes(routes);
        var options = source.defaults(route, LLMModelOptions.builder().provider("deepseek").build());
        assertEquals("test-model", options.getModel());
        assertEquals(route, options.getProvider());
        assertFalse(json.writeValueAsString(options.findMetadata().orElse(Map.of())).contains("private-secret"));
        assertEquals("private-secret", credentials.require("deepseek", options).apiKey());
        assertThrows(LLMConfigurationException.class, () -> credentials.require("kimi", options));
        var fileOptions = mock(ILLMFileConfigService.class);
        var models = new LLMModelService(scanner, fileOptions, mock(IDefaultModelResolver.class));
        models.installRoutes(routes);
        // 2026/11/04：注册的实际协议仍按来源映射查找，不访问 model.yml。
        var protocol = mock(ILLMModel.class);
        var annotation = mock(LLMModel.class);
        when(annotation.id()).thenReturn("deepseek");
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("protocol", new IndependentPair<>(annotation, protocol)));
        models.init();
        assertSame(protocol, models.getModel(LLMModelId.of(route)));
        verifyNoInteractions(files, fileOptions);
        String changed = AgentAssemblyTest.privateAssembly("changed-secret");
        when(row.getValue()).thenReturn(json.writeValueAsString(Map.of("enabled", true, "components", changed)));
        assertThrows(LLMConfigurationException.class, () -> credentials.require("deepseek", options));
        when(row.getValue()).thenReturn(json.writeValueAsString(new AgentScopeConfigData()));
        assertThrows(LLMConfigurationException.class, () -> source.credentials(route));
    }
}
