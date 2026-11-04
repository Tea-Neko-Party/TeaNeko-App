package org.zexnocs.teanekocore.database.configdata;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在原 EasyData 事务内统一配置版本、字段合并及幂等审计，不在网页先读后写。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：支持继承、别名及泛型字段，并分离文本与 JSON 值的事务编码。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@Service
public final class ConfigEditService {
    private final ConfigDataRegisterService registration;
    private final IEasyDataService data;
    private final ObjectMapper mapper;
    private final IConfigFieldCheckerScanner checkers;

    /**
     * @param registration 原配置存储声明
     * @param data 原事务 DTO 入口
     * @param mapper 原转换器
     * @param checkers 原字段校验器
     */
    public ConfigEditService(ConfigDataRegisterService registration, IEasyDataService data,
                             ObjectMapper mapper, IConfigFieldCheckerScanner checkers) {
        this.registration = registration;
        this.data = data;
        this.mapper = mapper;
        this.checkers = checkers;
    }

    /**
     * @param manager 本地已声明管理器
     * @param key 已解析的真实配置键
     * @param guard 调用方授权复核
     * @return 实际当前读或未登记的默认预览
     */
    public CompletionStage<ConfigEdits.Snapshot> read(ConfigManager manager, String key, Runnable guard) {
        return run(manager, () -> {
            guard.run();
            var row = registration.getDto(manager).atomic().find(key, EasyDataLock.READ);
            return row == null ? new ConfigEdits.Snapshot(false, "absent", defaults(manager))
                    : new ConfigEdits.Snapshot(true, revision(row), row.getValue());
        });
    }

    /**
     * @param manager 固定管理器
     * @param after 原末项键
     * @param limit 一至一百项
     * @return 真实有界键页
     */
    public CompletionStage<ConfigEdits.Keys> keys(ConfigManager manager, String after, int limit) {
        if (limit < 1 || limit > 100 || after.length() > 256) {
            throw new IllegalArgumentException("配置目录分页参数无效。");
        }
        return run(manager, () -> {
            var rows = registration.getDto(manager).atomic().scan(after, null, limit + 1, EasyDataLock.READ);
            var keys = rows.stream().limit(limit).map(EasyDataRow::getKey).toList();
            return new ConfigEdits.Keys(keys, rows.size() > limit ? keys.getLast() : "");
        });
    }

    /**
     * @param manager 固定管理器
     * @param key 已解析的真实键
     * @param actor 受信调用方提供的操作者标识
     * @param change 明确操作
     * @param guard 实际提交前的授权复核
     * @return 与配置同事务提交的真实回执
     */
    public CompletionStage<ConfigEdits.Receipt> change(ConfigManager manager, String key, String actor,
                                                     ConfigEdits.Change change, Runnable guard) {
        // 2026/11/04：旧文本调用保留原有 String 及 JSON 标量语义。
        return change(manager, key, actor, change, guard, false);
    }

    /**
     * @param manager 固定配置管理器
     * @param key 已解析的真实目标
     * @param actor 原受信操作者
     * @param change 每个字段均使用完整 JSON 值的请求
     * @param guard 实际提交前授权
     * @return 包含空值、枚举和复杂类型校验的真实事务回执
     */
    public CompletionStage<ConfigEdits.Receipt> changeJson(ConfigManager manager, String key, String actor,
            ConfigEdits.Change change, Runnable guard) {
        return change(manager, key, actor, change, guard, true);
    }

    /**
     * @param manager 固定管理器
     * @param key 实际键
     * @param actor 受信操作者
     * @param change 原版本和字段请求
     * @param guard 提交守卫
     * @param jsonValues 是否使用明确的 JSON 值协议
     * @return 原事务结果，编码方式参与幂等仲裁
     */
    private CompletionStage<ConfigEdits.Receipt> change(ConfigManager manager, String key, String actor,
            ConfigEdits.Change change, Runnable guard, boolean jsonValues) {
        if (actor.isBlank() || actor.length() > 128 || key.isBlank() || key.length() > 256) {
            throw new IllegalArgumentException("配置操作者或目标无效。");
        }
        String actorDigest = StableDigest.sha256(actor);
        String textDigest = StableDigest.sha256(key, change.expectedRevision(), change.action().name(),
                mapper.writeValueAsString(new TreeMap<>(change.fields())));
        // 2026/11/04：保留原文本请求摘要，既有保留期内回执仍可重放。
        String digest = jsonValues ? StableDigest.sha256("JSON", textDigest) : textDigest;
        return run(manager, () -> {
            guard.run();
            var journalDto = data.getEasyDataFor(manager.storage(), "config.management.v1", manager.value()).atomic();
            var journalRow = journalDto.find("operations", EasyDataLock.WRITE);
            var journal = journalRow == null ? new LinkedHashMap<UUID, ConfigEdits.Operation>()
                    : new LinkedHashMap<>(mapper.readValue(journalRow.getValue(), ConfigEdits.Journal.class).operations());
            Instant now = Instant.now();
            journal.values().removeIf(op -> op.result().at().plus(Duration.ofHours(24)).isBefore(now));
            var previous = journal.get(change.operationId());
            if (previous != null) {
                if (!previous.actor().equals(actorDigest) || !previous.digest().equals(digest)) {
                    throw new IllegalArgumentException("配置操作标识已绑定其他主体或参数。");
                }
                return replay(previous.result());
            }
            if (journal.size() >= 512) {
                throw new IllegalStateException("配置操作回执窗口已满，请等待过期后再提交。");
            }
            var dto = registration.getDto(manager).atomic();
            var row = dto.find(key, EasyDataLock.WRITE);
            String actual = row == null ? "absent" : revision(row);
            if (!actual.equals(change.expectedRevision())) {
                throw new ConfigEditConflictException();
            }
            boolean registered = change.action() != ConfigEdits.Action.UNREGISTER;
            switch (change.action()) {
                case REGISTER -> {
                    if (row != null) {
                        throw new ConfigEditConflictException();
                    }
                    row = dto.create(key, defaults(manager));
                    guard.run();
                    dto.save(row);
                }
                case UNREGISTER -> {
                    if (row == null) {
                        throw new ConfigEditConflictException();
                    }
                    guard.run();
                    dto.delete(row);
                }
                case PATCH -> {
                    if (row == null) {
                        throw new ConfigEditConflictException();
                    }
                    var node = (ObjectNode) mapper.readTree(row.getValue());
                    var schema = new ConfigSchema(mapper, manager.configType());
                    var defaultValues = jsonValues ? mapper.readTree(defaults(manager)) : null;
                    var checker = checkers.get(manager.fieldChecker());
                    for (var field : change.fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                        var declared = schema.require(field.getKey());
                        if (!declared.editable()) {
                            throw new IllegalArgumentException("该配置字段不可写。");
                        }
                        var parsed = !jsonValues && declared.type().hasRawClass(String.class)
                                ? mapper.valueToTree(field.getValue()) : mapper.readTree(field.getValue());
                        if (jsonValues) {
                            ConfigSchema.validateValue(declared, parsed);
                            if (parsed.isNull() && !declared.nullable()
                                    && defaultValues != null && defaultValues.hasNonNull(declared.jsonName())) {
                                throw new IllegalArgumentException("该配置字段没有声明允许空值。");
                            }
                        }
                        if (checker != null) {
                            String invalid = jsonValues ? checker.isValidJson(field.getKey(), parsed)
                                    : checker.isValid(field.getKey(), field.getValue());
                            if (invalid != null) {
                                throw new IllegalArgumentException(invalid);
                            }
                        }
                        Object typed = mapper.treeToValue(parsed, declared.type());
                        declared.aliases().forEach(node::remove);
                        node.set(declared.jsonName(), mapper.valueToTree(typed));
                    }
                    // 2026/11/04：完整绑定保留构造器和 setter 合同；未知的其他字段仍在原树中保留。
                    mapper.treeToValue(node, manager.configType());
                    guard.run();
                    row.setValue(mapper.writeValueAsString(node));
                    dto.save(row);
                }
            }
            String next = registered ? revision(Objects.requireNonNull(row)) : "absent";
            var result = new ConfigEdits.Receipt(change.operationId(), StableDigest.sha256(key), next, registered,
                    change.action(), change.fields().keySet().stream().sorted().toList(), now, false);
            journal.put(change.operationId(), new ConfigEdits.Operation(actorDigest, digest, result));
            String body = mapper.writeValueAsString(new ConfigEdits.Journal(journal));
            if (journalRow == null) {
                journalDto.save(journalDto.create("operations", body));
            } else {
                journalRow.setValue(body);
                journalDto.save(journalRow);
            }
            guard.run();
            return result;
        });
    }

    /**
     * @param manager 原管理器
     * @param operation 原操作
     * @param actor 受信主体
     * @param guard 当前授权
     * @return 本人仍在保留期内的原回执
     */
    public CompletionStage<ConfigEdits.Receipt> operation(ConfigManager manager, UUID operation,
                                                        String actor, Runnable guard) {
        return run(manager, () -> {
            guard.run();
            var row = data.getEasyDataFor(manager.storage(), "config.management.v1", manager.value())
                    .atomic().find("operations", EasyDataLock.READ);
            var value = row == null ? null : mapper.readValue(row.getValue(), ConfigEdits.Journal.class)
                    .operations().get(operation);
            if (value == null || !value.actor().equals(StableDigest.sha256(actor))
                    || value.result().at().plus(Duration.ofHours(24)).isBefore(Instant.now())) {
                throw new NoSuchElementException("配置操作回执不存在或已过期。");
            }
            return replay(value.result());
        });
    }

    /**
     * @param manager 固定管理器
     * @return 新建对象的默认 JSON，不写入数据库
     * @throws ReflectiveOperationException 当配置缺少合法默认构造器时
     */
    private String defaults(ConfigManager manager) throws ReflectiveOperationException {
        return mapper.writeValueAsString(manager.configType().getDeclaredConstructor().newInstance());
    }

    /**
     * @param row 当前持锁版本行
     * @return 包含身份及正文的不可伪造版本摘要
     */
    private static String revision(EasyDataRow row) {
        return "r_" + StableDigest.sha256(Long.toString(row.getId()), Long.toString(row.getVersion()), row.getValue());
    }

    /**
     * @param result 原提交回执
     * @return 不重复操作的回放标记
     */
    private static ConfigEdits.Receipt replay(ConfigEdits.Receipt result) {
        return new ConfigEdits.Receipt(result.operationId(), result.targetDigest(), result.revision(),
                result.registered(), result.action(), result.fields(), result.at(), true);
    }

    /**
     * @param manager 已启用的本地管理声明
     * @param action 仅包含短数据库动作的回调
     * @param <T> 非空结果
     * @return 等待真实提交及缓存失效的阶段
     */
    private <T> CompletionStage<T> run(ConfigManager manager, Callable<T> action) {
        if (!manager.enabled()) {
            throw new IllegalStateException("配置管理器尚未启用。");
        }
        var result = new AtomicReference<T>();
        var task = registration.getDto(manager).getTaskConfig("版本化配置管理");
        task.addTransactionTask(() -> {
            result.set(null);
            result.set(Objects.requireNonNull(action.call(), "配置事务没有产生结果。"));
        });
        return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get()))
                .toCompletableFuture().minimalCompletionStage();
    }
}
