package org.zexnocs.teanekoagent.llm.file_config.form;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 模型样板及凭据输入方式的扫描式目录，只返回实际注册且插件启用的实现。
 * 不读取用户模型文件，不导出凭据，不发起供应商请求。
 *
 * 修改记录：<br>
 * 2026/11/04：通过独立 id 注册样板与输入方式，避免业务键占用框架组件名。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
@Service
public final class LLMConfigurationDirectory {
    private final IBeanScanner scanner;
    private final ILLMModelService models;
    private final PluginAccess plugins;

    /**
     * @param scanner 原注解及接口发现
     * @param models 实际模型注册服务
     * @param plugins 原插件归属和启用检查
     */
    public LLMConfigurationDirectory(IBeanScanner scanner, ILLMModelService models, PluginAccess plugins) {
        this.scanner = scanner;
        this.models = models;
        this.plugins = plugins;
    }

    /**
     * 2026/11/04：只从业务 id 建立注册目录，Bean 名独立管理。
     * @return 无秘密的实际模型样板及输入协议，重复声明明确失败
     */
    public Catalog describe() {
        var adapters = new AnnotatedBeanRegistry<>(scanner, LLMConfigurationAdapter.class,
                ILLMConfigurationAdapter.class, declaration -> {
                    if (declaration.version() != 1) {
                        throw new IllegalArgumentException("模型配置描述协议版本无效。");
                    }
                    return LLMModelId.of(declaration.id());
                });
        var inputs = new AnnotatedBeanRegistry<>(scanner, LLMCredentialInput.class, ILLMCredentialInput.class,
                LLMCredentialInput::id);
        var routes = new ArrayList<Route>();
        var registered = models.getModels();
        for (var entry : adapters.entries().entrySet().stream()
                .sorted(java.util.Comparator.comparing(value -> value.getKey().id())).toList()) {
            var model = registered.get(entry.getKey());
            var bean = entry.getValue().bean();
            if (model == null || !plugins.enabled(bean, method(ILLMConfigurationAdapter.class, "describe"))
                    || !plugins.enabled(model, method(ILLMModel.class, "call", ILLMPrompt.class))) {
                continue;
            }
            var template = bean.describe();
            if (!entry.getKey().id().equals(template.defaults().get("id"))) {
                throw new IllegalArgumentException("模型样板的路由与声明不一致。");
            }
            routes.add(new Route(entry.getKey().id(), template.name(), template.description(),
                    template.defaults(), template.models()));
        }
        var modes = new ArrayList<CredentialMode>();
        for (var entry : inputs.entries().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var bean = entry.getValue().bean();
            if (!plugins.enabled(bean, method(ILLMCredentialInput.class, "describe"))) {
                continue;
            }
            var value = bean.describe();
            Pattern.compile(value.pattern());
            modes.add(new CredentialMode(entry.getKey(), value.name(), value.description(), value.prefix(),
                    value.suffix(), value.pattern(), value.secret(), value.placeholder()));
        }
        return new Catalog(List.copyOf(routes), List.copyOf(modes));
    }

    /**
     * @param type 原声明接口
     * @param name 固定方法名
     * @param arguments 固定参数类型
     * @return 归属检查使用的方法声明，不反射执行用户输入
     */
    private static Method method(Class<?> type, String name, Class<?>... arguments) {
        try {
            return type.getMethod(name, arguments);
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("模型配置扩展接口与实现不一致。", failure);
        }
    }

    /**
     * 公开模型样板。
     * @param id 实际注册路由
     * @param name 显示名称
     * @param description 配置说明
     * @param defaults 公开可编辑初值
     * @param models 名称建议
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    public record Route(String id, String name, String description, Map<String, Object> defaults, List<String> models) {}

    /**
     * 凭据输入规则，不包含已配置值。
     * @param id 唯一输入方式
     * @param name 显示名称
     * @param description 输入说明
     * @param prefix 编码前缀
     * @param suffix 编码后缀
     * @param pattern 输入表达式
     * @param secret 是否掩码
     * @param placeholder 提示文本
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    public record CredentialMode(String id, String name, String description, String prefix, String suffix,
                                 String pattern, boolean secret, String placeholder) {}

    /**
     * 配置页面的扩展目录。
     * @param routes 实际已启用模型的样板
     * @param inputs 已启用输入方式
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    public record Catalog(List<Route> routes, List<CredentialMode> inputs) {}
}
