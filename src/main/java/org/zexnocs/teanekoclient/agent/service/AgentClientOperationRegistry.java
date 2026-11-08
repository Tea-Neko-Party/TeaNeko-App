package org.zexnocs.teanekoclient.agent.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 初始化时固定类型化操作与插件归属，运行路径只按稳定标识查询。
 * <br>2026/11/07：拒绝重复贡献并持有插件在途调用，停启后旧结果不能恢复有效。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentClientOperationRegistry {
    private final Map<String, IAgentClientOperation<?>> handlers;
    private final PluginAccess plugins;
    private final Method invocation;

    /**
     * 仅在容器初始化期间解析归属，发布前校验全部操作标识。
     * @param contributions 容器提供的操作实现
     * @param plugins 统一插件资格
     * @throws IllegalStateException 协议方法缺失时
     * @throws IllegalArgumentException 操作标识冲突时
     */
    public AgentClientOperationRegistry(List<IAgentClientOperation<?>> contributions, PluginAccess plugins) {
        this.plugins = plugins;
        try {
            invocation = IAgentClientOperation.class.getMethod("execute", AgentClientOperationContext.class);
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("内部操作扩展合同缺少执行方法。", failure);
        }
        var collected = new HashMap<String, IAgentClientOperation<?>>();
        for (var contribution : contributions) {
            var protocol = contribution.protocol();
            if (collected.putIfAbsent(protocol.id(), contribution) != null) {
                throw new IllegalArgumentException("内部操作标识重复：" + protocol.id());
            }
            plugins.register(contribution, invocation);
        }
        handlers = Map.copyOf(collected);
    }

    /**
     * 核对调用方协议类型并取得当前插件调用占用。
     * @param protocol 调用方已知的传输协议
     * @param <R> 响应类型
     * @return 必须跟随实际业务完成的调用
     * @throws IllegalStateException 操作未安装或当前停用时
     * @throws IllegalArgumentException 响应协议不匹配时
     */
    public <R> Invocation<R> open(AgentOperationProtocol<R> protocol) {
        var handler = handlers.get(protocol.id());
        if (handler == null) {
            throw new IllegalStateException("内部操作尚未安装：" + protocol.id());
        }
        var actual = handler.protocol();
        if (!actual.responseType().equals(protocol.responseType()) || !actual.wireName().equals(protocol.wireName())) {
            throw new IllegalArgumentException("内部操作响应协议不匹配：" + protocol.id());
        }
        return new Invocation<>(protocol, handler, plugins.open(handler, invocation));
    }

    /**
     * 一次固定的类型化插件调用，不因后续重新开启插件更新其代次。
     * @param <R> 调用方要求的响应类型
     * @param protocol 已核对的调用协议
     * @param handler 本次固定的业务实现
     * @param call 插件实际占用，宿主贡献为空
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Invocation<R>(AgentOperationProtocol<R> protocol, IAgentClientOperation<?> handler,
                                PluginRegistry.@Nullable Call call) {
        /**
         * 2026/11/08：同时复核插件代次和处理器声明的真实角色约束。
         * @param source 原生调用来源
         * @throws IllegalStateException 插件代次、依赖或来源资格已失效时
         */
        public void check(org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData source) {
            if (call != null) {
                call.check();
            }
            handler.verifySource(source);
        }

        /**
         * 使用本次固定处理器产生持久化摘要。
         * @param input 当前真实负载
         * @param field 业务字段
         * @return 明确摘要
         */
        public String digest(NativeMessageInput input, String field) {
            return handler.digest(input, field);
        }

        /**
         * 运行独立贡献，并按初始化时声明的动态响应类型检查结果。
         * @param context 当前可信操作上下文
         * @return 类型化响应，错误类型不会进入公开响应
         */
        public CompletionStage<R> execute(AgentClientOperationContext context) {
            check(context.source());
            return handler.execute(context).thenApply(result ->
                    protocol.responseType().cast(Objects.requireNonNull(result, "内部操作响应不能为空。")));
        }

        /**
         * 在真实业务结束后释放占用，取消外部观察不会提前完成此阶段。
         * @param work 真实业务完成阶段
         * @return 含插件代次复核的完成阶段
         */
        public CompletionStage<R> track(CompletionStage<R> work) {
            return call == null ? work : call.track(work);
        }
    }
}
