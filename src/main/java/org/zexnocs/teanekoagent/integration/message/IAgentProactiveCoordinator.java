package org.zexnocs.teanekoagent.integration.message;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import java.util.concurrent.CompletionStage;

/**
 * 普通消息入口可调用的主动活动端口，业务实现由可选插件提供。
 * <br>2026/11/07：入站框架不再依赖主动活动的具体实现。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentProactiveCoordinator {
    /**
     * 评估真实来信是否满足主动接话条件。
     * @param data 真实平台来源
     * @param input 已解析输入
     * @return 真实处理完成后的发言结果
     */
    CompletionStage<Boolean> offer(ITeaNekoMessageData data, NativeMessageInput input);

    /**
     * 观察已被被动回复接管的真实来信，不重复发言。
     * @param data 真实平台来源
     */
    void observe(ITeaNekoMessageData data);
}
