package org.zexnocs.teanekoagent.component.llm.configuration;

// 2026/11/05：模型专属装配统一归入 configuration，保留原 Bean 与文件契约。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Optional;

/**
 * 从统一模型文件读取默认路由，LLM 不再依赖 Agent 主配置。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：模型默认路由装配归入组件的 configuration 目录。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
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
