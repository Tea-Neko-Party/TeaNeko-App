package org.zexnocs.teanekoagent.llm.file_config.form;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 模型样板及凭据输入方式的扫描式目录，只返回实际注册且插件启用的实现。
 * 不读取用户模型文件，不导出凭据，不发起供应商请求。
 *
 * 修改记录：<br>
 * 2026/11/04：通过独立 id 注册样板与输入方式，避免业务键占用框架组件名。
 *
 * <br>2026/11/04：将型号列表与真实上下文容量一起投影到页面。
 *
 * <br>2026/11/04：从适配器声明投影模型图标，公开目录不读取秘密。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：复用已编译正则的表达式生成目录，保持非法模式在注册边界失败。
 * <br>2026/11/05：扩展 Bean 仅首次访问时发现，后续目录读取仍实时检查模型注册及插件启用状态。
 * <br>2026/11/05：接入扫描器生命周期，模型与凭据目录重扫成功后整体发布。
 * <br>2026/11/05：扩展发现固定在初始化或显式重扫，页面查询不触发首次扫描或失败重试。
 * <br>2026/11/07：归属方法与图标在重扫时固定，页面轮询不发现注解或方法。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
@Service
public final class LLMConfigurationDirectory implements IScanner {
    private static final Method ADAPTER_DESCRIPTION = method(ILLMConfigurationAdapter.class, "describe");
    private static final Method INPUT_DESCRIPTION = method(ILLMCredentialInput.class, "describe");
    private static final Method MODEL_CALL = method(ILLMModel.class, "call", ILLMPrompt.class);
    private final IBeanScanner scanner;
    private final ILLMModelService models;
    private final PluginAccess plugins;
    // 2026/11/05：只固定容器中的扩展声明，不缓存按当前启用状态筛选后的公开目录。
    private volatile @Nullable Discovery discovery;

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

    /** 2026/11/05：仅首次初始化时发现声明，普通目录查询复用已发布缓存。 */
    @Override
    public synchronized void init() {
        if (discovery == null) {
            reload();
        }
    }

    /**
     * 2026/11/05：模型与凭据声明全部校验成功后原子替换，失败不暴露半套新目录。
     * @throws IllegalArgumentException 当扩展版本、标识或声明唯一性无效时
     */
    @Override
    public synchronized void reload() {
        var adapters = new AnnotatedBeanRegistry<>(scanner, LLMConfigurationAdapter.class,
                ILLMConfigurationAdapter.class, declaration -> {
                    if (declaration.version() != 1) {
                        throw new IllegalArgumentException("模型配置描述协议版本无效。");
                    }
                    return LLMModelId.of(declaration.id());
                });
        var inputs = new AnnotatedBeanRegistry<>(scanner, LLMCredentialInput.class, ILLMCredentialInput.class,
                LLMCredentialInput::id);
        // 2026/11/07：全部静态元数据与归属初始化完成后共同发布，不在 describe 中补建缓存。
        var icons = new LinkedHashMap<LLMModelId, String>();
        adapters.entries().forEach((id, entry) -> {
            plugins.register(entry.bean(), ADAPTER_DESCRIPTION);
            icons.put(id, entry.type().getAnnotation(LLMConfigurationAdapter.class).icon());
        });
        inputs.entries().values().forEach(entry -> plugins.register(entry.bean(), INPUT_DESCRIPTION));
        discovery = new Discovery(adapters, inputs, Map.copyOf(icons));
    }

    /**
     * 2026/11/04：只从业务 id 建立注册目录，Bean 名独立管理。
     * 2026/11/05：复用已编译正则的表达式生成目录，保持非法模式在注册边界失败。
     * 2026/11/05：复用扩展发现结果，每次仍按实际模型注册和插件状态重新投影。
     * @return 无秘密的实际模型样板及输入协议，重复声明明确失败
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public Catalog describe() {
        // 2026/11/05：查询只读完整发现快照，不在高频页面路径启动或重试底层扫描。
        var fixed = discovery;
        if (fixed == null) {
            throw new IllegalStateException("模型配置目录尚未成功初始化，请检查初始化日志并显式重扫。");
        }
        var routes = new ArrayList<Route>();
        var registered = models.getModels();
        for (var entry : fixed.adapters().entries().entrySet().stream()
                .sorted(java.util.Comparator.comparing(value -> value.getKey().id())).toList()) {
            var model = registered.get(entry.getKey());
            var bean = entry.getValue().bean();
            if (model == null || !plugins.enabled(bean, ADAPTER_DESCRIPTION)
                    || !plugins.enabled(model, MODEL_CALL)) {
                continue;
            }
            var template = bean.describe();
            if (!entry.getKey().id().equals(template.defaults().get("id"))) {
                throw new IllegalArgumentException("模型样板的路由与声明不一致。");
            }
            routes.add(new Route(entry.getKey().id(), template.name(), template.description(),
                    // 2026/11/04：型号列表与容量均来自供应商声明，不在线请求模型。
                    template.defaults(), template.models(), template.contextWindows(),
                    fixed.icons().get(entry.getKey())));
        }
        var modes = new ArrayList<CredentialMode>();
        for (var entry : fixed.inputs().entries().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var bean = entry.getValue().bean();
            if (!plugins.enabled(bean, INPUT_DESCRIPTION)) {
                continue;
            }
            var value = bean.describe();
            var pattern = Pattern.compile(value.pattern());
            modes.add(new CredentialMode(entry.getKey(), value.name(), value.description(), value.prefix(),
                    value.suffix(), pattern.pattern(), value.secret(), value.placeholder()));
        }
        return new Catalog(List.copyOf(routes), List.copyOf(modes));
    }

    /**
     * 一次完整发现的声明缓存，不保存启用结果或凭据。
     *
     * @param adapters 模型配置扩展
     * @param inputs 凭据输入扩展
     * @param icons 配置适配器声明图标
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.2-l
     * @version 4.9.0-l
     */
    private record Discovery(AnnotatedBeanRegistry<LLMModelId, ILLMConfigurationAdapter> adapters,
                             AnnotatedBeanRegistry<String, ILLMCredentialInput> inputs,
                             Map<LLMModelId, String> icons) {}

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
     * 修改记录：<br>
     * 2026/11/04：公开各型号真实上下文容量供页面选择。
     *
     * <br>2026/11/04：增加模型样板的同源图标路径。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 实际注册路由
     * @param name 显示名称
     * @param description 配置说明
     * @param defaults 公开可编辑初值
     * @param models 名称建议
     * @param contextWindows 型号对应的真实上下文容量
     * @param icon 适配器声明的同源图标路径
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.8.0-l
     */
    public record Route(String id, String name, String description, Map<String, Object> defaults, List<String> models,
                        Map<String, Long> contextWindows, String icon) {}

    /**
     * 凭据输入规则，不包含已配置值。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
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
     * @version 4.8.0-l
     */
    public record CredentialMode(String id, String name, String description, String prefix, String suffix,
                                 String pattern, boolean secret, String placeholder) {}

    /**
     * 配置页面的扩展目录。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param routes 实际已启用模型的样板
     * @param inputs 已启用输入方式
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.8.0-l
     */
    public record Catalog(List<Route> routes, List<CredentialMode> inputs) {}
}
