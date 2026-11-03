package org.zexnocs.teanekoagent.expression;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
/**
 * 资源发送借用的原请求、目标与守卫，不进入冻结计划或持久化。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param data 原消息目标
 * @param request 原请求
 * @param index 原步骤号
 * @param control 协调控制
 * @param guard 每次不可逆动作前须等待的原守卫
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@JsonIgnoreType
public record ReplySendContext(ITeaNekoMessageData data,AgentRunRequest request,int index,ExecutionControl control,Supplier<CompletionStage<Void>> guard){}
