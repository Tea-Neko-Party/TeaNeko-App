package org.zexnocs.teanekoagent.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.expression.ExpressionSettings;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

/**
 * 本地表达与等待策略，具体人格仍使用原模块。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value="expression",path="agent",type=FileConfigType.YAML)
public class AgentExpressionFileConfig implements IFileConfigData {
    private boolean enabled=true;
    private int maxMessages=8;
    private int maxStepDelay=3000;
    private int maxTotalDelay=10000;
    private int defaultDelay=350;
    private int timeoutSeconds=90;
    private int maxQueued=16;
    /** @return 实际使用时校验的不可变配置 */
    public ExpressionSettings snapshot(){return new ExpressionSettings(enabled,maxMessages,maxStepDelay,maxTotalDelay,defaultDelay,timeoutSeconds,maxQueued);}
}
