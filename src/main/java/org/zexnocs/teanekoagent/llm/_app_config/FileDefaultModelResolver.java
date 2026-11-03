package org.zexnocs.teanekoagent.llm._app_config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Optional;

/**
 * 从统一模型文件读取默认路由，LLM 不再依赖 Agent 主配置。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
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
     * 从当前 model.yml 读取默认路由；配置不可用时明确失败，不使用旧主配置或缓存值。
     *
     * @return 符合当前定位与类型约束的值；未配置或不存在时返回空
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    @Override
    public Optional<LLMModelId> findDefaultModelId() {
        try {
            var config = fileConfigService.requireCurrent(LLMModelFileConfig.class);
            return config.findDefaultModelId();
        } catch (FileConfigDataNotFoundException ignored) {
            throw new LLMConfigurationException("model.yml 当前不可用，请修复并重载本地模型配置。");
        }
    }
}
