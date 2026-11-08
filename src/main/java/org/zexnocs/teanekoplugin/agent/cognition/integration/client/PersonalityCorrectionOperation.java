package org.zexnocs.teanekoplugin.agent.cognition.integration.client;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.agent.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekoclient.agent.service.AgentClientOperationContext;
import org.zexnocs.teanekoclient.agent.service.AgentOperationProtocol;
import org.zexnocs.teanekoclient.agent.service.IAgentClientOperation;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityLearningTarget;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityCorrection;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 由认知插件处理内部客户端的表达补正，原生角色和人格依据在副作用前重新复核。
 * <br>2026/11/08：保留原候选字段白名单、管理锁、来源绑定及幂等摘要。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
@PluginOwned("agent.cognition")
public final class PersonalityCorrectionOperation implements IAgentClientOperation<AgentPersonalityCorrectionResponse> {
    private final IAgentPersonalitySource personalities;
    private final Optional<IPersonalityLearningTarget> learning;

    /**
     * 借用人格读取与候选提交端口，不自行建立人格或修改授权。
     * @param personalities 当前人格源
     * @param learning 可选候选提交能力
     */
    public PersonalityCorrectionOperation(IAgentPersonalitySource personalities,
                                          Optional<IPersonalityLearningTarget> learning) {
        this.personalities = personalities;
        this.learning = learning;
    }

    /** @return 原人格补正传输协议。 */
    @Override
    public AgentOperationProtocol<AgentPersonalityCorrectionResponse> protocol() {
        return AgentPersonalityCorrectionResponse.PROTOCOL;
    }

    /**
     * 只有真实 OWNER 或 DEBUG 可提交公共表达候选。
     * @param source 实际平台来源
     * @throws IllegalStateException 当角色不允许操作时
     * @throws UnsupportedOperationException 当人格提供者不支持候选提交时
     */
    @Override
    public void verifySource(ITeaNekoMessageData source) {
        var role = source.getUserData().getRole();
        if (role != CommandPermission.OWNER && role != CommandPermission.DEBUG) {
            throw new IllegalStateException("手动公共表达补正需要真实 OWNER 或 DEBUG 权限。");
        }
        if (learning.isEmpty()) {
            throw new UnsupportedOperationException("当前人格适配器不支持表达候选。");
        }
    }

    /**
     * 字段名参与同一来源的负载仲裁，沿用原摘要输入顺序。
     * @param input 原文及附件摘要
     * @param field 目标表达字段
     * @return 原补正动作摘要
     */
    @Override
    public String digest(NativeMessageInput input, String field) {
        return StableDigest.sha256(input.digest(), field);
    }

    /**
     * 依据当前人格提交表达候选，保留原写入及验证流程。
     * @param operation 宿主固定的可信调用
     * @return 真实新增或重复结果
     */
    @Override
    public CompletionStage<AgentPersonalityCorrectionResponse> execute(AgentClientOperationContext operation) {
        operation.check();
        var learner = learning.orElseThrow();
        var actor = operation.actor();
        var control = operation.control();
        return personalities.resolve(actor.scope(), control).thenCompose(basis -> {
            operation.check();
            var candidate = new PersonalityCorrection(operation.id(), basis.baseId(), basis.baseVersion(),
                    operation.field(), operation.input().text(), 1, actor, operation.input().occurredAt(), null, false);
            return learner.propose(actor.scope(), basis, candidate, control).thenCompose(added ->
                    personalities.verify(actor.scope(), basis, control).thenApply(ignored -> {
                        operation.check();
                        return new AgentPersonalityCorrectionResponse(added ? "STORED" : "DUPLICATE", added);
                    }));
        });
    }
}
