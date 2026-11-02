package org.zexnocs.teanekoagent.llm._app_config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 只在请求边界读取当前 model.yml 凭据与连接配置，环境占位符由 Core YAML 解析器处理。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public class FileLLMCredentialResolver implements ICredentialResolver {
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
