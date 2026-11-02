package org.zexnocs.teanekoagent.response;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.agent.AgentRunResult;

import java.util.List;
import java.util.UUID;

/**
 * 只返回正式图文与回复形式，不包含交互诊断、工具参数或供应商透明状态。
 * @param requestId 原请求
 * @param status 业务终态；SUCCESS 不代表已发送平台
 * @param parts 可供调用方消费的有序正式内容
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
public record AgentConversationResponse(@Nullable UUID requestId, String status, List<Content> parts) {
    /**
     * 固定输出集合，不能把运行服务放入响应。
     * @param requestId 原请求
     * @param status 明确业务状态
     * @param parts 正式内容
     */
    public AgentConversationResponse { parts = List.copyOf(parts); }
    /**
     * 从成功答案投影公开响应，不把模型或工具诊断返回给平台。
     * @param result 已复核原结果
     * @return 业务状态及正式图文
     */
    public static AgentConversationResponse from(AgentRunResult result) {
        var plan = AgentReplyPlan.from(result);
        return new AgentConversationResponse(result.requestId(), result.status().name(), plan.map(agentReplyPlan -> agentReplyPlan.parts().stream()
                .map(part -> switch (part) {
                    case AgentReplyPlan.Text text -> new Content("text", text.value(), text.mode().name());
                    case AgentReplyPlan.Image image -> new Content("image", image.url(), image.mode().name());
                }).toList()).orElseGet(List::of));
    }
    /**
     * 可序列化的明确内容，接收方不能将正文 URL 自动转成图片。
     * @param type text 或 image
     * @param value 字面正文或明确图片引用
     * @param mode REPLY、AT 或 PLAIN；目标始终是原生来源
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Content(String type, String value, String mode) { }
}
