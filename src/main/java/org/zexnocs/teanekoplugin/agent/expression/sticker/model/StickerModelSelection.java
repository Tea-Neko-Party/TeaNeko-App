package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.llm.ScopedLLMRouteSource;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAccess;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * 按组件选择生成一次主模型及备用模型计划，原始配置只用于路由绑定。
 * <br>2026/11/10：默认跟随 LLM 组件，跨模型切换不改变聊天配置。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Service
public final class StickerModelSelection {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final AgentComponentRegistry components;
    private final IFileConfigService files;
    private final ObjectMapper mapper;

    /**
     * @param configs 原聊天配置
     * @param manager Agent 配置声明
     * @param components 已初始化的组件目录
     * @param files 原插件文件配置
     * @param mapper 原转换器
     */
    public StickerModelSelection(IConfigDataService configs, AgentScopeConfigManager manager,
            AgentComponentRegistry components, IFileConfigService files, ObjectMapper mapper) {
        this.configs = configs;
        this.manager = manager;
        this.components = components;
        this.files = files;
        this.mapper = mapper;
    }

    /**
     * @param scope 当前真实聊天
     * @param legacyFallback 保留原图库 model-id，未配置新备用时使用
     * @return 固定配置版本的选择，不读取或返回凭据
     */
    public Plan plan(AgentScope scope, String legacyFallback) {
        var config = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .orElseThrow(() -> new StickerAccessException(StickerAccess.SOURCE_DISABLED));
        var serialized = components.normalizeConfiguration(mapper.writeValueAsString(config));
        var assembly = components.effective(serialized);
        if (!config.isEnabled() || !assembly.has("sticker")) {
            throw new StickerAccessException(StickerAccess.SOURCE_DISABLED);
        }
        var installed = AgentAssembly.document(serialized).path("components");
        var selected = installed.path("sticker").path("model");
        boolean inherited = selected.path("source").asString("inherit").equals("inherit");
        var model = inherited ? installed.path("llm") : selected;
        String route = model.path("source").asString("system").equals("custom")
                ? inherited ? ScopedLLMRouteSource.route(scope.scopeId(), config.assembly())
                    : ScopedLLMRouteSource.route(scope.scopeId(), config.assembly(), "sticker", "model")
                : model.path("route").asString("");
        var fallback = files.requireCurrent(StickerModelFileConfig.class).getFallback();
        var alternative = fallback(fallback).or(() -> legacyFallback.isBlank()
                ? Optional.empty() : Optional.of(legacyFallback));
        return new Plan(route, alternative.filter(value -> !value.equals(route)),
                StableDigest.sha256(assembly.revision(), fallback.toString(), legacyFallback));
    }

    /**
     * @param selected 全局模型选择
     * @return 无模型或明确的路由
     */
    public static Optional<String> fallback(JsonNode selected) {
        return switch (selected.path("source").asString("none")) {
            case "none" -> Optional.empty();
            case "system" -> Optional.of(selected.path("route").asString(""));
            case "custom" -> Optional.of(StickerFallbackRouteSource.route(selected));
            default -> throw new IllegalArgumentException("全局备用模型来源无效。");
        };
    }

    /**
     * 固定计划不含原配置或秘密，指纹只用于判断变更。
     * @param primary 首选路由
     * @param fallback 可选备用路由
     * @param revision 完整选择指纹
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public record Plan(String primary, Optional<String> fallback, String revision) {}
}
