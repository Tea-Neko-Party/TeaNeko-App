package org.zexnocs.teanekoagent.integration.web;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.personality.*;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationControl;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 无聊天绑定的人格空间管理；按补正真实基础身份编辑，不假定空间属于默认人格。
 * <br>2026/11/06：复用原持久账本、内容版本、备份和共享会话失效机制。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
@Service
public final class AgentPersonalityLibraryService {
    private final IPersonalityStore store;
    private final IPersonalityManagementStore management;
    private final IPersonalityConfiguration configuration;
    private final IAgentConversationControl conversations;

    /**
     * @param store 原持久空间
     * @param management 原管理事务
     * @param configuration 可信文件目录，不读取聊天配置
     * @param conversations 原会话同步端口
     */
    public AgentPersonalityLibraryService(IPersonalityStore store, IPersonalityManagementStore management,
            IPersonalityConfiguration configuration, IAgentConversationControl conversations) {
        this.store = store;
        this.management = management;
        this.configuration = configuration;
        this.conversations = conversations;
    }

    /**
     * @param context 宿主已授权的实例入口
     * @param query 固定空间、版本与基础人格身份
     * @return 原内容或持久事务回执
     */
    public CompletionStage<Object> execute(ManagementContext context, Query query) {
        context.checkActive();
        if (!AgentConsoleService.global(context) || query.key().isBlank() || query.action().equals("BIND")) {
            throw new SecurityException("独立人格管理需要实例权限和明确空间，不提供聊天绑定操作。");
        }
        if (query.action().equals("OPERATION")) {
            var id = Objects.requireNonNull(query.operationId());
            return management.operation(id, AgentConsoleService.actor(context, id), context::checkActive)
                    .thenApply(result -> AgentConsoleService.personalityResult(id, result, "原持久回执；请重新读取空间。"));
        }
        if (query.action().equals("READ")) {
            return read(context, query.key());
        }
        // 2026/11/06：原始指纹进入幂等命令；基础目录只在首次事务执行时查询。
        var action = PersonalityManagementCommand.Action.valueOf(query.action());
        var id = Objects.requireNonNull(query.operationId());
        var command = new PersonalityManagementCommand(id, AgentConsoleService.actor(context, id), action,
                query.key(), query.bindingRevision(), query.stateRevision(), query.correctionId(), query.field(),
                query.text(), query.locked(), query.baseFingerprint());
        return management.applyUnbound(command, () -> {
            context.checkActive();
            if (!Set.of(PersonalityManagementCommand.Action.ADD, PersonalityManagementCommand.Action.EDIT,
                    PersonalityManagementCommand.Action.LOCK).contains(action)) {
                return null;
            }
            return configuration.catalog().stream().filter(item -> item.id().equals(query.baseId())
                            && item.fingerprint().equals(query.baseFingerprint())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("基础人格不存在或已变化，请重新读取并选择。"));
        }, Instant.now(), context::checkActive).thenCompose(result -> {
            if (result.status() != PersonalityManagementResult.Status.APPLIED
                    || action == PersonalityManagementCommand.Action.CLONE || action == PersonalityManagementCommand.Action.BACKUP) {
                return CompletableFuture.completedStage(AgentConsoleService.personalityResult(id, result,
                        "空间事务已确认；未修改任何聊天绑定。"));
            }
            CompletionStage<Integer> synchronizedViews;
            try {
                synchronizedViews = conversations.synchronizePersonality(AgentConsoleService.scope(context),
                        query.key(), true, context.control());
            } catch (RuntimeException failure) {
                synchronizedViews = CompletableFuture.failedStage(failure);
            }
            return synchronizedViews.handle((count, failure) -> AgentConsoleService.personalityResult(id, result,
                    failure == null ? "空间已保存，已协调共享会话：" + count + "。"
                            : "空间已保存；会话协调未确认，请刷新，不要重新提交。"));
        });
    }

    /**
     * 只读真实空间及本地基础目录，不创建聊天绑定。
     * @param context 当前管理授权
     * @param key 已选择空间
     * @return 补正及可选基础身份
     */
    private CompletionStage<Object> read(ManagementContext context, String key) {
        return store.state(key).thenCompose(found -> {
            context.checkActive();
            var state = found.orElseThrow(() -> new NoSuchElementException("人格空间不存在，请刷新目录。"));
            var bases = configuration.catalog();
            return management.backups(state.key()).thenApply(backups -> {
                context.checkActive();
                var correctionBases = new LinkedHashMap<UUID, BaseOption>();
                for (var correction : state.corrections()) {
                    bases.stream().filter(base -> base.id().equals(correction.baseId())
                            && base.version() == correction.baseVersion()).findFirst()
                            .ifPresent(base -> correctionBases.put(correction.requestId(), option(base)));
                }
                var view = new AgentConsole.PersonalityView(state.key(), List.of(), Set.of(), state.key(),
                        0, state.revision(), false, state.backup(), state.corrections().stream().map(item ->
                        new AgentConsole.Correction(item.requestId(), item.field(), item.text(), item.locked(),
                                item.occurredAt(), item.source().origin().name())).toList(), backups);
                return new View(view, bases.stream().map(AgentPersonalityLibraryService::option).toList(),
                        Map.copyOf(correctionBases));
            });
        });
    }

    /**
     * @param base 可信定义
     * @return 只读可选项及防止迟到提交的指纹
     */
    private static BaseOption option(BasePersonality base) {
        return new BaseOption(base.id(), base.version(), base.name(), base.mutableFields(), base.fingerprint());
    }

    /**
     * 独立空间请求，不接受浏览器提供来源身份。
     * @param action 动作
     * @param operationId 操作编号
     * @param key 空间
     * @param bindingRevision 独立写操作固定零
     * @param stateRevision 内容版本
     * @param correctionId 原补正
     * @param field 新增字段
     * @param text 正文
     * @param locked 锁定值
     * @param baseId 显式基础人格
     * @param baseFingerprint 页面观察到的基础指纹
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    public record Query(String action, @Nullable UUID operationId, String key, long bindingRevision,
                        long stateRevision, @Nullable UUID correctionId, @Nullable String field,
                        @Nullable String text, @Nullable Boolean locked, @Nullable String baseId,
                        @Nullable String baseFingerprint) {
        /** 验证有界输入；正文与操作参数的细化校验由原管理命令完成。 */
        public Query {
            if (action == null || !Set.of("READ", "OPERATION", "CLONE", "BACKUP", "ADD", "EDIT", "LOCK", "REMOVE").contains(action)
                    || key == null || key.isBlank() || key.length() > 256
                    || !action.equals("READ") && operationId == null
                    || text != null && text.length() > 512 || baseId != null && baseId.length() > 128
                    || baseFingerprint != null && baseFingerprint.length() > 128) {
                throw new IllegalArgumentException("人格空间管理参数无效。");
            }
        }
    }

    /**
     * 本地可选基础身份。
     * @param id 标识
     * @param version 版本
     * @param name 名称
     * @param fields 可补正字段
     * @param fingerprint 内容指纹
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    public record BaseOption(String id, int version, String name, Set<String> fields, String fingerprint) {}

    /**
     * 补正内容和基础身份分开表示，混合空间不伪装为默认人格。
     * @param personality 原内容视图
     * @param bases 当前可信可选基础人格
     * @param correctionBases 仍兼容的补正身份，缺失时只能撤销或保存副本
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    public record View(AgentConsole.PersonalityView personality, List<BaseOption> bases,
                       Map<UUID, BaseOption> correctionBases) {}
}
