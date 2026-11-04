package org.zexnocs.teanekoagent.component.expression.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.component.expression.ExpressionSettings;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

/**
 * 本地表达与等待策略，具体人格仍使用原模块。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：为控制台提供表达策略与时间单位说明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value="expression",path="agent",type=FileConfigType.YAML)
// 2026/11/04：控制台展示类型及字段注解，不读取运行秘密。
@org.zexnocs.teanekocore.framework.description.Description("拟人表达控制分段发送、等待时间及发送队列；具体风格由当前人格决定。")
public class AgentExpressionFileConfig implements IFileConfigData {
    @org.zexnocs.teanekocore.framework.description.Description("允许解析分段与表达协议。")
    private boolean enabled=true;
    @org.zexnocs.teanekocore.framework.description.Description("一轮回复最多发送的消息段数。")
    private int maxMessages=8;
    @org.zexnocs.teanekocore.framework.description.Description("每段发送前最长等待时间，单位毫秒。")
    private int maxStepDelay=3000;
    @org.zexnocs.teanekocore.framework.description.Description("整轮累计等待上限，单位毫秒。")
    private int maxTotalDelay=10000;
    @org.zexnocs.teanekocore.framework.description.Description("未指定分段间隔时的等待时间，单位毫秒。")
    private int defaultDelay=350;
    @org.zexnocs.teanekocore.framework.description.Description("整轮表达交付超时，单位秒。")
    private int timeoutSeconds=90;
    @org.zexnocs.teanekocore.framework.description.Description("发送队列允许等待的回复数量。")
    private int maxQueued=16;
    /** @return 实际使用时校验的不可变配置 */
    public ExpressionSettings snapshot(){return new ExpressionSettings(enabled,maxMessages,maxStepDelay,maxTotalDelay,defaultDelay,timeoutSeconds,maxQueued);}
}
