package org.zexnocs.teanekoagent.file_config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Agent 主配置思考参数测试。
 * <br>验证默认思考预算和异常配置值的规范化边界。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class AgentMainFileConfigThinkingTest {
    /**
     * 验证默认配置启用受控思考，并使用节省 token 的步骤数和摘要长度。
     */
    @Test
    void usesTokenConservativeThinkingDefaults() {
        var config = new AgentMainFileConfig();

        Assertions.assertTrue(config.isThinkingEnabled());
        Assertions.assertEquals(3, config.normalizedMaxThinkingSteps());
        Assertions.assertEquals(240, config.normalizedMaxThoughtSummaryLength());
        Assertions.assertTrue(config.isIncludeThoughtsInOutput());
    }

    /**
     * 验证超出允许范围的思考配置会被限制到预定义上下界。
     */
    @Test
    void clampsThinkingConfiguration() {
        var config = new AgentMainFileConfig();
        config.setMaxThinkingSteps(100);
        config.setMaxThoughtSummaryLength(1);

        Assertions.assertEquals(8, config.normalizedMaxThinkingSteps());
        Assertions.assertEquals(32, config.normalizedMaxThoughtSummaryLength());
    }
}
