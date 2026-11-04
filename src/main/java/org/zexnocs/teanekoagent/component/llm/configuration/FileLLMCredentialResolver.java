package org.zexnocs.teanekoagent.component.llm.configuration;

// 2026/11/05：模型专属装配统一归入 configuration，保留原 Bean 与文件契约。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 只在请求边界读取当前 model.yml 凭据与连接配置，环境占位符由 Core YAML 解析器处理。
 * <br>2026/11/03：核对原参数代次后原子取得凭据，热应用后拒绝旧参数拼接新连接。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按所选路由隔离凭据，获取连接前核对协议及配置代次。
 *
 * <br>2026/11/04：按来源解析私有与系统凭据，禁止协议或配置代次混用。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：模型凭据装配归入组件的 configuration 目录。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
@Service
public class FileLLMCredentialResolver implements ICredentialResolver {
    private org.zexnocs.teanekoagent.component.llm.framework.route.@org.jspecify.annotations.Nullable LLMRouteDirectory routes;

    /**
     * 2026/11/04：认证从所属配置来源取得，私有模型不回退系统密钥。
     * @param routes 配置来源目录
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installRoutes(org.zexnocs.teanekoagent.component.llm.framework.route.LLMRouteDirectory routes) {
        this.routes = routes;
    }
    private org.zexnocs.teanekoagent.component.llm.file_config.@org.jspecify.annotations.Nullable LLMFileConfigurationEpoch epoch;

    /**
     * 2026/11/03：Spring 宿主安装原文件换代边界。
     * @param epoch 原文件代次
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installEpoch(org.zexnocs.teanekoagent.component.llm.file_config.LLMFileConfigurationEpoch epoch) {
        this.epoch = epoch;
    }

    /**
     * @param provider 原供应商
     * @param options 原已解析参数
     * @return 同代次可信连接，不持锁等待网络
     */
    @Override
    public LLMProviderAccess require(String provider,
            org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions options) {
        // 2026/11/04：实际适配器核对路由归属后，按路由读取独立凭据，禁止同协议条目互相串用。
        var route = options.findProvider().orElse(provider);
        if (routes != null) {
            var source = routes.source(route);
            return source.fixed(route, () -> {
                source.verify(options);
                if (!source.adapter(route).id().equals(provider)) {
                    throw new LLMConfigurationException("模型路由与实际协议不一致。");
                }
                return source.credentials(route);
            });
        }
        if (epoch == null) {
            return requireRoute(route, provider);
        }
        return epoch.fixed(() -> {
            epoch.verify(options);
            return requireRoute(route, provider);
        });
    }

    /**
     * 2026/11/04：在认证边界检查显式协议映射。
     * @param route 配置路由
     * @param adapter 实际执行协议
     * @return 路由是否归属该协议
     */
    @Override
    public boolean supportsRoute(String route, String adapter) {
        if (routes != null) {
            return routes.source(route).adapter(route).id().equals(adapter);
        }
        return files.requireCurrent(LLMModelFileConfig.class).findModelConfig(LLMModelId.of(route))
                .map(entry -> entry.adapterId().id().equals(adapter)).orElse(false);
    }

    /**
     * 2026/11/04：保证别名的密钥和网络连接只交给声明的协议。
     * @param route 已解析路由
     * @param adapter 实际协议
     * @return 当前路由可信连接
     * @throws LLMConfigurationException 当路由声明与协议不符时
     */
    private LLMProviderAccess requireRoute(String route, String adapter) {
        if (!supportsRoute(route, adapter)) {
            throw new LLMConfigurationException("所选模型路由不属于当前协议适配器。");
        }
        return require(route);
    }
    private final IFileConfigService files;

    /**
     * 保存文件端口，不在构造时读取任何供应商凭据。
     *
     * @param files 文件配置当前加载状态
     */
    public FileLLMCredentialResolver(IFileConfigService files) {
        this.files = files;
    }

    /**
     * 只在实际供应商边界读取最新 model.yml，不回写文件或回退旧 application 配置。
     *
     * @param provider 供应商注册标识，不由模型名称推断
     * @return 仅供实际认证边界读取的可信凭据与连接数据
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public LLMProviderAccess require(String provider) {
        if (routes != null) {
            var source = routes.source(provider);
            return source.fixed(provider, () -> source.credentials(provider));
        }
        if (provider == null || !provider.matches("[a-z][a-z0-9-]*")) {
            throw new LLMConfigurationException("LLM 凭据供应商标识不合法。");
        }
        try {
            var entry = files.requireCurrent(LLMModelFileConfig.class).findModelConfig(LLMModelId.of(provider))
                    .orElseThrow(() -> new LLMConfigurationException("model.yml 缺少所选供应商条目。"));
            return new LLMProviderAccess(entry.getApiKey(), entry.getBaseUrl(), entry.getApiPath());
        } catch (RuntimeException ignored) {
            // 只报告固定原因，配置、解析和连接校验的原始异常都不能作为 cause 传播。
            throw new LLMConfigurationException("LLM 凭据或连接配置不可用，请检查 model.yml 及引用的环境变量。");
        }
    }
}
