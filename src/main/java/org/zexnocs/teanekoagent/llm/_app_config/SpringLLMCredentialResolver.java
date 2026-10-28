package org.zexnocs.teanekoagent.llm._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;

/**
 * 懒加载 Spring application/环境配置，缺少某个供应商密钥不影响应用启动。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public class SpringLLMCredentialResolver implements ICredentialResolver {
    private final Environment environment;

    /**
     * 保存 Spring 环境端口，凭据留待实际供应商调用时解析，不在构造阶段强制加载全部供应商。
     *
     * @param environment 提供本地配置及环境变量的 Spring 环境
     */
    public SpringLLMCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    /**
     * 在实际使用供应商时从 Spring 环境解析凭据和连接配置；校验失败只报告非敏感原因。
     *
     * @param provider 供应商注册标识，不由模型名称推断
     * @return 仅供实际认证边界读取的可信凭据与连接数据
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public LLMProviderAccess require(String provider) {
        if (provider == null || !provider.matches("[a-z][a-z0-9-]*")) {
            throw new LLMConfigurationException("Invalid LLM credential provider identifier.");
        }
        var prefix = "tea-neko.llm.providers." + provider + ".";
        try {
            return new LLMProviderAccess(environment.getProperty(prefix + "api-key"),
                    environment.getProperty(prefix + "base-url"),
                    environment.getProperty(prefix + "api-path"));
        } catch (RuntimeException ignored) {
            // Environment 占位符异常可能含原始配置值，不能将其作为 cause 传播。
            throw new LLMConfigurationException("LLM credentials or connection settings are unavailable for provider "
                    + provider + ". Check Spring application configuration.");
        }
    }
}
