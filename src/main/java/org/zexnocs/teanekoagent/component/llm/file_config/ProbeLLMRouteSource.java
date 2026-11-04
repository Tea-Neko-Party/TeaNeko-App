package org.zexnocs.teanekoagent.component.llm.file_config;

import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.route.ILLMRouteSource;
import org.zexnocs.teanekoagent.component.llm.framework.route.LLMRouteSource;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 检测专用的短生命周期配置来源，不发布文件缓存，不把凭据放入 Options 或公开目录。
 * 检测拥有者必须在实际结束后释放路由；协议仍由原注册表发现。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@LLMRouteSource(namespace = "probe")
public final class ProbeLLMRouteSource implements ILLMRouteSource {
    private final ConcurrentHashMap<String, LLMModelFileConfigParameter> entries = new ConcurrentHashMap<>();

    /**
     * @param configuration 本次授权并解析的独占快照
     * @return 检测拥有者需要释放的随机路由
     */
    public String register(LLMModelFileConfigParameter configuration) {
        String route = "probe:" + UUID.randomUUID();
        entries.put(route, configuration);
        return route;
    }

    /** @param route 本次拥有的检测路由 */
    public void release(String route) {
        entries.remove(route);
    }

    /**
     * @param route 当前检测路由
     * @return 尚在生命周期内的私有配置
     * @throws LLMConfigurationException 当路由已释放或未登记时
     */
    private LLMModelFileConfigParameter entry(String route) {
        var entry = entries.get(route);
        if (entry == null) {
            throw new LLMConfigurationException("模型检测已结束或检测路由不可用。");
        }
        return entry;
    }

    /**
     * 从检测快照解析协议，不修改运行路由。
     * @param route 当前路由
     * @return 快照声明的真实协议
     */
    @Override
    public LLMModelId adapter(String route) {
        return entry(route).adapterId();
    }

    /**
     * @param route 检测路由
     * @param base 注册协议基础参数
     * @return 使用完整配置输出额度、不含凭据的参数
     */
    @Override
    public LLMModelOptions defaults(String route, ILLMModelOptions base) {
        var options = entry(route).toOptions(base);
        options.setProvider(route);
        return options;
    }

    /**
     * 只向协议请求构造提供检测凭据。
     * @param route 当前路由
     * @return 只供 HTTP 构造使用的连接与凭据
     */
    @Override
    public LLMProviderAccess credentials(String route) {
        var entry = entry(route);
        return new LLMProviderAccess(entry.getApiKey(), entry.getBaseUrl(), entry.getApiPath());
    }

    /**
     * @param route 当前路由
     * @param action 同步请求构造
     * @param <T> 返回类型
     * @return 同一个独占快照的构造结果
     */
    @Override
    public <T> T fixed(String route, Supplier<T> action) {
        entry(route);
        return action.get();
    }

    /** @param options 固定在随机检测路由的参数 */
    @Override
    public void verify(ILLMModelOptions options) {
        entry(options.findProvider().orElseThrow());
    }

    /**
     * 校验随机来源仍然有效，不对参数追加敏感内容。
     * @param options 已固定来源的参数
     * @return 当前有效参数
     */
    @Override
    public LLMModelOptions stamp(LLMModelOptions options) {
        verify(options);
        return options;
    }
}
