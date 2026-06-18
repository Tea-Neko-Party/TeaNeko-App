package org.zexnocs.teanekoagent.llm.framework.tool;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolProvider;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolProvider;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大语言模型工具注册与调用服务。
 * <br>扫描所有 {@link ILLMTool} Bean，并按工具名称统一执行模型发起的 tool call。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@Service
public class LLMToolService extends AbstractScanner implements ILLMToolService {
    /**
     * Spring Bean 扫描器，用于发现容器中注册的 {@link ILLMTool} 实例。
     */
    private final IBeanScanner beanScanner;

    /**
     * Jackson ObjectMapper，用于注解工具方法的参数解析和返回值序列化。
     */
    private final ObjectMapper objectMapper;

    /**
     * 工具注册表。
     * <br>键为工具名称，值为对应的工具定义；使用并发映射以支持扫描和查询过程中的线程安全访问。
     */
    private final Map<String, ILLMTool> toolMap = new ConcurrentHashMap<>();

    /**
     * 创建大语言模型工具注册与调用服务。
     *
     * @param beanScanner Spring Bean 扫描器
     * @param objectMapper Jackson ObjectMapper
     */
    public LLMToolService(IBeanScanner beanScanner,
                          @Qualifier("customObjectMapper") ObjectMapper objectMapper) {
        this.beanScanner = beanScanner;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取所有已注册的大语言模型工具。
     * <br>返回的是注册表快照，外部调用方不能直接修改内部状态。
     *
     * @return 工具名称到工具定义的映射
     */
    @Override
    public Map<String, ILLMTool> getTools() {
        return Map.copyOf(toolMap);
    }

    /**
     * 按工具包获取需要暴露给模型的大语言模型工具。
     *
     * @param toolPackage 工具包名称
     * @return 工具名称到工具定义的映射
     */
    @Override
    public Map<String, ILLMTool> getTools(String toolPackage) {
        if (toolPackage == null || toolPackage.isBlank()) {
            return Map.of();
        }
        if (ALL_TOOL_PACKAGES.equalsIgnoreCase(toolPackage)) {
            return getTools();
        }
        var result = new LinkedHashMap<String, ILLMTool>();
        for (var entry : toolMap.entrySet()) {
            var registeredPackage = entry.getValue().getToolPackage();
            if (toolPackage.equals(registeredPackage)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 根据工具名称获取已注册的大语言模型工具。
     *
     * @param name 工具名称
     * @return 对应的工具定义
     * @throws IllegalArgumentException 当工具未注册时抛出
     */
    @Override
    public ILLMTool getTool(String name) {
        var tool = toolMap.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("LLM tool is not registered: " + name);
        }
        return tool;
    }

    /**
     * 执行模型发起的工具调用。
     * <br>该方法会根据工具名称查找已注册工具，并将模型生成的参数交给工具执行器处理。
     *
     * @param toolCall 模型返回的工具调用请求
     * @return 工具执行结果
     * @throws Exception 当工具不存在、参数解析失败或工具执行失败时抛出
     */
    @Override
    public String call(ILLMToolCall toolCall) throws Exception {
        return getTool(toolCall.getName()).call(toolCall.getArguments());
    }

    /**
     * 扫描并注册 Spring 容器中的所有大语言模型工具。
     * <br>同时支持手写 {@link ILLMTool} Bean 和注解式工具方法。
     *
     * @throws IllegalStateException 当发现重复工具名称时抛出
     */
    @Override
    protected void _scan() {
        scanToolBeans();
        scanToolProviders();
    }

    /**
     * 清空工具注册表。
     * <br>通常由重新扫描流程调用，用于在重新加载工具 Bean 前释放旧注册信息。
     */
    @Override
    protected void _clear() {
        toolMap.clear();
    }

    /**
     * 扫描并注册手写的 {@link ILLMTool} Bean。
     */
    private void scanToolBeans() {
        for (var tool : beanScanner.getBeansOfType(ILLMTool.class).values()) {
            registerTool(tool);
        }
    }

    /**
     * 扫描并注册注解式工具提供者。
     */
    private void scanToolProviders() {
        var providers = new LinkedHashMap<String, Object>();
        for (var entry : beanScanner.getBeansWithAnnotation(LLMToolProvider.class).entrySet()) {
            providers.put(entry.getKey(), entry.getValue().second());
        }
        for (var entry : beanScanner.getBeansOfType(ILLMToolProvider.class).entrySet()) {
            providers.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (var bean : providers.values()) {
            scanToolProvider(bean);
        }
    }

    /**
     * 扫描单个工具提供者 Bean。
     *
     * @param bean 工具提供者 Bean
     */
    private void scanToolProvider(Object bean) {
        var beanClass = beanScanner.getBeanClass(bean);
        var providerPackage = resolveProviderPackage(bean, beanClass);
        for (var method : findToolMethods(beanClass)) {
            registerTool(createTool(bean, method, providerPackage));
        }
    }

    /**
     * 根据工具方法创建 {@link LLMTool}。
     *
     * @param bean 工具方法所在 Bean
     * @param method 工具方法
     * @param providerPackage 工具提供者默认包
     * @return 自动生成的工具定义
     */
    private LLMTool createTool(Object bean, Method method, String providerPackage) {
        var mapping = method.getAnnotation(LLMToolMapping.class);
        var toolName = firstNonBlank(mapping.value(), mapping.name(), method.getName());
        var toolDescription = firstNonBlank(
                mapping.description(),
                getMethodDescription(method),
                method.getName()
        );
        return LLMTool.builder()
                .name(toolName)
                .description(toolDescription)
                .parameters(LLMFunctionParameterSchemaFactory.fromMethod(method))
                .strict(mapping.strict())
                .toolPackage(firstNonBlank(mapping.toolPackage(), providerPackage, ""))
                .executor(new ReflectiveLLMToolExecutor(objectMapper, bean, method))
                .build();
    }

    /**
     * 注册工具定义。
     *
     * @param tool 工具定义
     * @throws IllegalStateException 当工具名称为空或重复时抛出
     */
    private void registerTool(ILLMTool tool) {
        var toolName = tool.getName();
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalStateException("LLM tool name must not be blank: " + tool.getClass().getName());
        }
        var existing = toolMap.putIfAbsent(toolName, tool);
        if (existing != null) {
            throw new IllegalStateException("Duplicate LLM tool name: " + toolName);
        }
    }

    /**
     * 查找类及其父类、接口中的工具方法。
     *
     * @param beanClass Bean 真实类型
     * @return 工具方法集合
     */
    private LinkedHashSet<Method> findToolMethods(Class<?> beanClass) {
        var methods = new LinkedHashSet<Method>();
        collectToolMethods(beanClass, methods);
        return methods;
    }

    /**
     * 递归收集工具方法。
     *
     * @param clazz 当前类型
     * @param methods 工具方法集合
     */
    private void collectToolMethods(Class<?> clazz, LinkedHashSet<Method> methods) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        for (var method : clazz.getDeclaredMethods()) {
            if (!method.isSynthetic()
                    && !method.isBridge()
                    && method.isAnnotationPresent(LLMToolMapping.class)) {
                methods.add(method);
            }
        }
        for (var interfaceClass : clazz.getInterfaces()) {
            collectToolMethods(interfaceClass, methods);
        }
        collectToolMethods(clazz.getSuperclass(), methods);
    }

    /**
     * 解析工具提供者默认包。
     *
     * @param bean 工具提供者 Bean
     * @param beanClass Bean 真实类型
     * @return 默认工具包
     */
    private String resolveProviderPackage(Object bean, Class<?> beanClass) {
        var providerAnnotation = beanClass.getAnnotation(LLMToolProvider.class);
        if (providerAnnotation != null && !providerAnnotation.toolPackage().isBlank()) {
            return providerAnnotation.toolPackage();
        }
        if (bean instanceof ILLMToolProvider provider) {
            return provider.getLLMToolPackage();
        }
        return "";
    }

    /**
     * 获取方法描述。
     *
     * @param method 方法
     * @return 方法描述
     */
    private static String getMethodDescription(Method method) {
        var description = method.getAnnotation(Description.class);
        return description == null ? "" : description.value();
    }

    /**
     * 返回第一个非空字符串。
     *
     * @param values 候选字符串
     * @return 第一个非空字符串
     */
    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
