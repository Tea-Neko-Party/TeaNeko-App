package org.zexnocs.teanekoagent._app_config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Optional;

/**
 * 当前文件布局的装配适配器；LLM 层只依赖默认路由端口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public class FileDefaultModelResolver implements IDefaultModelResolver {
    private final IFileConfigService fileConfigService;

    /**
     * 注入文件配置读取端口，每次解析使用当前配置，LLM 服务无需依赖 Agent 文件布局。
     *
     * @param fileConfigService 文件配置读取服务
     */
    public FileDefaultModelResolver(IFileConfigService fileConfigService) {
        this.fileConfigService = fileConfigService;
    }

    /**
     * 从当前本地主配置读取默认模型路由；配置不可用时明确失败，不静默改选供应商。
     *
     * @return 符合当前定位与类型约束的值；未配置或不存在时返回空
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public Optional<LLMModelId> findDefaultModelId() {
        try {
            var config = fileConfigService.get(AgentMainFileConfig.class);
            return config.findDefaultModelId();
        } catch (FileConfigDataNotFoundException ignored) {
            throw new LLMConfigurationException("Agent main-config.yml is unavailable; reload valid local configuration.");
        }
    }
}
