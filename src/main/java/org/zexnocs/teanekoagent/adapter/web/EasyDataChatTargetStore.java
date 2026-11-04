package org.zexnocs.teanekoagent.adapter.web;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IChatTargetStore;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Agent 聊天设置目录使用专用表，只保存身份映射与显示名，不采集消息正文。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：识别新版分管理器发现游标，保持原目录分页与存储规则。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.6-l
 */
@Service
public final class EasyDataChatTargetStore implements IChatTargetStore {
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;

    /**
     * @param data 专用 Agent 数据入口
     * @param mapper 原转换器
     */
    public EasyDataChatTargetStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.chat-directory.v1");
        this.mapper = mapper;
    }

    /**
     * @param client 真实平台
     * @param self 已确认的机器人号
     * @return 实际保存
     */
    @Override
    public CompletionStage<Void> rememberAccount(String client, String self) {
        if (self.isBlank() || self.length() > 128 || self.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("机器人账号号无效。");
        }
        return change(client, state -> {
            String ref = accountRef(self);
            if (!state.accounts().containsKey(ref) && state.accounts().size() >= 32) {
                throw new IllegalStateException("已知账号目录已满。");
            }
            state.accounts().put(ref, new ChatTargets.Account(ref, "机器人 " + self, self, Instant.now()));
            return true;
        }).thenAccept(ignored -> {});
    }

    /**
     * @param client 平台
     * @return 至多三十二个已观察账号
     */
    @Override
    public CompletionStage<List<ChatTargets.Account>> accounts(String client) {
        return change(client, state -> state.accounts().values().stream()
                .sorted(Comparator.comparing(ChatTargets.Account::name)).toList());
    }

    /**
     * @param client 平台
     * @param account 账号引用
     * @param after 聊天游标
     * @param kind 类型过滤
     * @return 当前稳定页
     */
    @Override
    public CompletionStage<ChatTargets.Page> page(String client, String account, String after, String kind) {
        if (!Set.of("", "GROUP", "PRIVATE").contains(kind) || after.length() > 64) {
            throw new IllegalArgumentException("聊天目录过滤或游标无效。");
        }
        return change(client, state -> {
            requireAccount(state, account);
            var values = state.targets().entrySet().stream().filter(entry -> entry.getKey().compareTo(after) > 0
                    && accountRef(entry.getValue().self()).equals(account)
                    && (kind.isEmpty() || entry.getValue().kind().equals(kind)))
                    .sorted(Map.Entry.comparingByKey()).limit(51).toList();
            var page = values.stream().limit(50).map(entry -> {
                var value = entry.getValue();
                return new ChatTargets.Target(entry.getKey(), value.kind(), display(value), value.platformId(),
                        value.source(), true, state.seen().get(entry.getKey()));
            }).toList();
            return new ChatTargets.Page(page, values.size() > 50 ? page.getLast().ref() : "",
                    // 2026/11/04：兼容原发现终点，新增目录按各管理器维护游标。
                    !Set.of("!done", "v2:!done").contains(state.cursor()));
        });
    }

    /**
     * @param client 平台
     * @param account 服务端账号引用
     * @param target 服务端聊天引用
     * @return 已验证映射，不允许跨账号引用
     */
    @Override
    public CompletionStage<ChatTargets.Resolved> resolve(String client, String account, String target) {
        return change(client, state -> {
            requireAccount(state, account);
            var value = state.targets().get(target);
            if (value == null || !accountRef(value.self()).equals(account)) {
                throw new NoSuchElementException("聊天目标不存在或不属于该账号。");
            }
            return new ChatTargets.Resolved(client, account, target, value.scope(), display(value), value.kind(),
                    value.platformId(), true);
        });
    }

    /**
     * @param values 原可信观察
     * @param guard 提交前检查
     * @return 全批实际持久化
     */
    @Override
    public CompletionStage<Void> remember(List<ChatTargets.Observation> values, Runnable guard) {
        if (values.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedStage(null);
        }
        String client = values.getFirst().clientId();
        if (values.size() > 200 || values.stream().anyMatch(value -> !value.clientId().equals(client)
                || !Set.of("GROUP", "PRIVATE").contains(value.kind()) || value.scope().length() > 256
                || value.platformId().isBlank() || value.platformId().length() > 128 || value.self().length() > 128
                || value.platformId().chars().anyMatch(Character::isISOControl)
                || value.self().chars().anyMatch(Character::isISOControl)
                || value.name().length() > 128)) {
            throw new IllegalArgumentException("聊天目录观察超出允许范围。");
        }
        return change(client, state -> {
            guard.run();
            Instant now = Instant.now();
            for (var value : values) {
                String account = accountRef(value.self());
                if (!state.accounts().containsKey(account) && state.accounts().size() >= 32) {
                    throw new IllegalStateException("已知机器人账号目录已满。");
                }
                String ref = StableDigest.sha256(account, value.kind(), value.platformId(), value.scope());
                if (!state.targets().containsKey(ref) && state.targets().size() >= 8192) {
                    throw new IllegalStateException("已知聊天目录已满。");
                }
                var old = state.targets().get(ref);
                var effective = old != null && value.name().isBlank() && !old.name().isBlank()
                        ? new ChatTargets.Observation(client, value.self(), value.kind(), value.platformId(),
                        value.scope(), old.name(), old.source()) : value;
                state.accounts().put(account, new ChatTargets.Account(account,
                        value.self().isEmpty() ? "历史配置（账号未归属）" : "机器人 " + value.self(), value.self(), now));
                state.targets().put(ref, effective);
                state.seen().put(ref, now);
            }
            guard.run();
            return true;
        }).thenAccept(ignored -> {});
    }

    /**
     * @param client 平台
     * @return 真实已保存发现游标
     */
    @Override
    public CompletionStage<String> importCursor(String client) {
        return change(client, State::cursor);
    }

    /**
     * @param client 平台
     * @param cursor 原配置末键
     * @return 实际提交
     */
    @Override
    public CompletionStage<Void> imported(String client, String cursor) {
        return change(client, state -> {
            state.position().put("cursor", cursor);
            return true;
        }).thenAccept(ignored -> {});
    }

    /**
     * @param client 受控平台标识
     * @param action 有界短事务
     * @param <T> 非空结果
     * @return 实际读写完成
     */
    private <T> CompletionStage<T> change(String client, Function<State, T> action) {
        if (!client.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("聊天目录的平台标识无效。");
        }
        return data.transaction("更新已知聊天设置目录", () -> {
            var dto = data.get(client).atomic();
            var row = dto.find("state", EasyDataLock.WRITE);
            var state = row == null ? new State(new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new HashMap<>()) : mapper.readValue(row.getValue(), State.class);
            T result = Objects.requireNonNull(action.apply(state));
            String body = mapper.writeValueAsString(state);
            if (body.length() > 4 * 1024 * 1024) {
                throw new IllegalStateException("已知聊天元数据超出容量限制。");
            }
            if (row == null && (!state.accounts().isEmpty() || !state.position().isEmpty())) {
                dto.save(dto.create("state", body));
            } else if (row != null && !row.getValue().equals(body)) {
                row.setValue(body);
                dto.save(row);
            }
            return result;
        });
    }

    /**
     * @param self 真实账号或空的历史定位
     * @return 不透明账号引用
     */
    private static String accountRef(String self) {
        return StableDigest.sha256("account", self);
    }

    /**
     * @param value 已验证观察
     * @return 中文名称，不把 scope 当作昵称
     */
    private static String display(ChatTargets.Observation value) {
        String name = value.name().isBlank() ? (value.kind().equals("GROUP") ? "群聊" : "用户") : value.name();
        return name + "（" + value.platformId() + "）";
    }

    /**
     * @param state 当前目录
     * @param account 原账号引用
     * @throws NoSuchElementException 当不存在时
     */
    private static void requireAccount(State state, String account) {
        if (!state.accounts().containsKey(account)) {
            throw new NoSuchElementException("机器人账号或历史目录不存在。");
        }
    }

    /**
     * 每平台一行有界目录，不含聊天正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param accounts 已知账号
     * @param targets 目标映射
     * @param seen 最近观测
     * @param position 配置发现位置
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record State(Map<String, ChatTargets.Account> accounts, Map<String, ChatTargets.Observation> targets,
                        Map<String, Instant> seen, Map<String, String> position) {
        /** @return 已保存游标或首屏 */
        public String cursor() {
            return position.getOrDefault("cursor", "");
        }
    }
}
