package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.route.ILLMRouteSource;
import org.zexnocs.teanekoagent.llm.framework.route.LLMRouteSource;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.JsonNode;
import java.util.function.Supplier;

/**
 * 全局私有视觉模型的版本化来源，凭据仅在同步请求构造边界读取。
 * <br>2026/11/10：插件停用或配置发布立即使旧路由失效，不缓存秘密。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@LLMRouteSource(namespace = "sticker-global")
@PluginOwned("agent.expression")
public final class StickerFallbackRouteSource implements ILLMRouteSource {
    private final IFileConfigService files;
    private final StickerModelConfiguration configuration;
    private final PluginRegistry plugins;

    /**
     * @param files 原文件缓存
     * @param configuration 私有配置解析
     * @param plugins 当前插件资格
     */
    public StickerFallbackRouteSource(IFileConfigService files, StickerModelConfiguration configuration,
                                       PluginRegistry plugins) {
        this.files = files;
        this.configuration = configuration;
        this.plugins = plugins;
    }

    /**
     * @param selection 固定选择
     * @return 不含秘密的内部路由
     */
    public static String route(JsonNode selection) {
        return "sticker-global:" + StableDigest.sha256(selection.toString());
    }

    /**
     * @param route 固定内部路由
     * @return 当前仍有效的受保护配置
     */
    private LLMModelFileConfigParameter entry(String route) {
        if (!plugins.isEnabled("agent.expression")) {
            throw new LLMConfigurationException("表情包模型插件已经停用。");
        }
        var selected = files.requireCurrent(StickerModelFileConfig.class).getFallback();
        if (!selected.path("source").asString().equals("custom") || !route(selected).equals(route)) {
            throw new LLMConfigurationException("全局视觉模型配置已经变化，请重新发起请求。");
        }
        return configuration.configuration(selected);
    }

    /**
     * @param route 已绑定配置版本的路由
     * @return 实际协议
     */
    @Override public LLMModelId adapter(String route) {
        return entry(route).adapterId();
    }

    /**
     * @param route 当前路由
     * @param base 协议初值
     * @return 不含秘密的有效选项
     */
    @Override public LLMModelOptions defaults(String route, ILLMModelOptions base) {
        var result = entry(route).toOptions(base);
        result.setProvider(route);
        return result;
    }

    /**
     * @param route 当前路由
     * @return 仅供 HTTP 构造使用的连接
     */
    @Override public LLMProviderAccess credentials(String route) {
        var value = entry(route);
        return new LLMProviderAccess(value.getApiKey(), value.getBaseUrl(), value.getApiPath());
    }

    /**
     * @param route 固定路由
     * @param action 同步构造
     * @param <T> 返回类型
     * @return 构造结果
     */
    @Override public <T> T fixed(String route, Supplier<T> action) {
        var token = plugins.capture("agent.expression");
        entry(route);
        var result = action.get();
        entry(route);
        plugins.check(token);
        return result;
    }

    /** @param options 固定配置版本的选项 */
    @Override public void verify(ILLMModelOptions options) {
        entry(options.findProvider().orElseThrow());
    }

    /**
     * @param options 固定选项
     * @return 经复核的原选项
     */
    @Override public LLMModelOptions stamp(LLMModelOptions options) {
        verify(options);
        return options;
    }
}
