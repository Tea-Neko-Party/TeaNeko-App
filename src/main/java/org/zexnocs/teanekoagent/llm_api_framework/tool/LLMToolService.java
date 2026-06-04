package org.zexnocs.teanekoagent.llm_api_framework.tool;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMToolService;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

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
     * 工具注册表。
     * <br>键为工具名称，值为对应的工具定义；使用并发映射以支持扫描和查询过程中的线程安全访问。
     */
    private final Map<String, ILLMTool> toolMap = new ConcurrentHashMap<>();

    /**
     * 创建大语言模型工具注册与调用服务。
     *
     * @param beanScanner Spring Bean 扫描器
     */
    public LLMToolService(IBeanScanner beanScanner) {
        this.beanScanner = beanScanner;
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
     * <br>当存在重复工具名称时会立即抛出异常，避免调用时路由到不确定的工具实例。
     *
     * @throws IllegalStateException 当发现重复工具名称时抛出
     */
    @Override
    protected void _scan() {
        for (var tool : beanScanner.getBeansOfType(ILLMTool.class).values()) {
            var existing = toolMap.putIfAbsent(tool.getName(), tool);
            if (existing != null) {
                throw new IllegalStateException("Duplicate LLM tool name: " + tool.getName());
            }
        }
    }

    /**
     * 清空工具注册表。
     * <br>通常由重新扫描流程调用，用于在重新加载工具 Bean 前释放旧注册信息。
     */
    @Override
    protected void _clear() {
        toolMap.clear();
    }
}
