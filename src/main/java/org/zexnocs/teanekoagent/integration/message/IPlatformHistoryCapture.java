package org.zexnocs.teanekoagent.integration.message;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.context.IAgentCapturedContext;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import java.util.concurrent.CompletionStage;

/**
 * 原生入站可选的旁观采集端口，宿主不依赖具体记忆业务。
 * <br>2026/11/07：将采集实现交由插件拥有，纯模型宿主无需装配采集服务。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IPlatformHistoryCapture {
    /**
     * 在固定边界内采集原生文字并返回后续入站的处理资格。
     * @param data 可信平台消息
     * @param input 已冻结的图文输入
     * @return 采集完成和后续处理边界
     */
    CompletionStage<Capture> capture(ITeaNekoMessageData data, NativeMessageInput input);

    /**
     * 采集后供自动轮次复核的纯边界，不携带客户端或执行控制。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：由可选采集端口发布，停用采集时仍允许纯模型入站。
     *
     * @param context 固定上下文，未采集时为空
     * @param accepted 是否允许该消息继续处理
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Capture(@Nullable IAgentCapturedContext context, boolean accepted) { }
}
