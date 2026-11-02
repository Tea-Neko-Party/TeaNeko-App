package org.zexnocs.teanekoagent.sender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 三类内部操作的进程内结构化数据；原生来源与父控制不进入 JSON 或任务名称。
 * @param <R> 明确响应类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
public final class AgentSendData<R> extends AbstractJsonSendData<R> {
    private final Kind kind;
    @JsonIgnore private final ITeaNekoMessageData source;
    @JsonIgnore private final String field;
    @JsonIgnore private final @Nullable ExecutionControl parent;
    /**
     * 保存宿主原生来源；不接受模型提供的用户、scope 或人格身份。
     * @param client 内部客户端
     * @param mapper 响应映射器
     * @param responseType 明确 DTO 类型
     * @param kind 固定操作
     * @param source 真实原生消息
     * @param field 表达字段，其他操作为空
     * @param parent 可选借用父控制
     */
    public AgentSendData(TeaNekoAgentInternalClient client, ObjectMapper mapper, Class<R> responseType, Kind kind,
            ITeaNekoMessageData source, String field, @Nullable ExecutionControl parent) {
        super(client, mapper, responseType); this.kind = Objects.requireNonNull(kind); this.source = Objects.requireNonNull(source);
        this.field = Objects.requireNonNull(field); this.parent = parent;
    }
    /**
     * 仅输出可关联元数据；禁止默认序列化把原生消息、正文或资源写入任务日志。
     * @return 脱敏发送说明
     */
    @Override @NonNull
    public String toSendString() { return "{\"operation\":\"" + kind.name() + "\",\"echo\":\"" + getEcho() + "\"}"; }
    /**
 * 宿主固定的三类能力入口，不接收任意可执行回调。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
    public enum Kind { CONVERSATION, MEMORY_WRITE, PERSONALITY_CORRECTION }
}
