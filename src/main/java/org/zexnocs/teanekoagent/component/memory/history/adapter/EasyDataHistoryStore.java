package org.zexnocs.teanekoagent.component.memory.history.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoagent.component.memory.history.*;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 通过专用 EasyData 保存历史及到期索引，不依赖独立 JPA 历史表或业务仓库。
 * 两个目标同事务提交，行序号支持重启分页，到期索引支持有界回收。
 * <br>2026/11/02：短期分层消息和可恢复目录加入原历史事务，不依赖事后内存通知。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Service
public final class EasyDataHistoryStore implements IHistoryStore {
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;
    private final @Nullable EasyDataLayeredMemoryStore layered;
    /**
     * 保存专用入口，复用现有 DTO、任务和事务设施。
     * @param data Agent EasyData
     * @param mapper JSON 转换器
     */
    public EasyDataHistoryStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this(data, mapper, null);
    }
    /**
     * 2026/11/02：可选接入默认分层存储，独立宿主保留原构造。
     * @param data 原数据入口
     * @param mapper 原 JSON 转换器
     * @param layered 可选分层消息存储，同步加入当前事务
     */
    @Autowired
    public EasyDataHistoryStore(TeaNekoAgentEasyData data, ObjectMapper mapper, @Nullable EasyDataLayeredMemoryStore layered) {
        this.data = Objects.requireNonNull(data).inNamespace("agent.history.v1");
        this.mapper = mapper.rebuild().disable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE).build();
        this.layered = layered;
    }
    /**
     * 同来源重投不覆盖正文或延长保留期，历史及到期索引原子保存。
     * @param context 原授权边界
     * @param message 消息
     * @param expiresAt 到期时刻
     * @return 提交后是否新增
     */
    @Override public CompletionStage<Boolean> append(HistoryContext context, HistoryMessage message, Instant expiresAt) {
        return submit(() -> {
            // 2026/11/02：包括原历史的幂等重投，分层消息、目录及原历史原子保存或回滚。
            if (layered != null) { layered.captureInTransaction(context, message, expiresAt); }
            var partition = partition(context.reference(), context.personality().identity());
            var key = digest(mapper.writeValueAsString(List.of(message.role(), message.messageId()))); var dto = history(partition);
            var old = dto.find(key, EasyDataLock.NONE);
            if (old != null) {
                if (!read(old).message().sameContent(message)) { throw new IllegalArgumentException("同一历史消息来源对应不同正文。"); }
                return false;
            }
            dto.save(dto.create(key, mapper.writeValueAsString(new Stored(1, context.reference(), context.personality().identity(), message, expiresAt))));
            var index = new Expiry(partition, key, expiresAt); var expiry = expiry();
            expiry.save(expiry.create(indexKey(index), mapper.writeValueAsString(index))); return true;
        });
    }
    /**
     * 单次至多扫描二百行，过期或未命中均推进游标，不将预算截断误报为完整缺失。
     * @param context 当前边界
     * @param query 查询条件
     * @param now 固定时刻
     * @return 有界页面及继续游标
     */
    @Override public CompletionStage<HistoryPage> search(HistoryContext context, HistoryQuery query, Instant now) {
        return submit(() -> {
            var items = new ArrayList<HistoryMessage>(); long cursor = query.before(); int scanned = 0; boolean omitted = false;
            var partition = partition(context.reference(), context.personality().identity());
            while (scanned < 200 && items.size() < query.limit()) {
                var rows = history(partition).recent(cursor, Math.min(32, 200 - scanned));
                if (rows.isEmpty()) { cursor = 0; break; }
                for (var row : rows) {
                    if (row.getId() <= 0 || row.getId() >= cursor) { throw new IllegalStateException("历史页面顺序无效。"); }
                    cursor = row.getId(); scanned++; var stored = read(row); var message = stored.message();
                    if (!stored.reference().equals(context.reference()) || !stored.personality().equals(context.personality().identity())) {
                        throw new IllegalStateException("历史页面来源边界不一致。");
                    }
                    if (!stored.expiresAt().isAfter(now) || query.start() != null && message.occurredAt().isBefore(query.start())
                            || query.end() != null && message.occurredAt().isAfter(query.end())
                            || !message.text().toLowerCase(Locale.ROOT).contains(query.keyword().toLowerCase(Locale.ROOT))) { continue; }
                    items.add(message);
                    if (mapper.writeValueAsString(items).length() > query.maxCharacters()) { items.removeLast(); omitted = true; }
                    if (items.size() == query.limit()) { break; }
                }
            }
            return new HistoryPage(items, cursor, scanned, omitted);
        });
    }
    /**
     * 使用可排序到期键处理至多一百条索引，不扫描全部空间或整个数据表。
     * @param now 当前时刻
     * @return 本批实际删除的消息数量
     */
    @Override public CompletionStage<Integer> prune(Instant now) {
        return submit(() -> {
            var expiry = expiry(); var rows = expiry.scan("", order(now) + "~", 100, EasyDataLock.WRITE); int deleted = 0;
            for (var row : rows) {
                var entry = mapper.readValue(row.getValue(), Expiry.class);
                if (!indexKey(entry).equals(row.getKey()) || entry.expiresAt().isAfter(now)) { throw new IllegalStateException("历史到期索引无效。"); }
                var target = history(entry.partition()); var message = target.find(entry.key(), EasyDataLock.WRITE);
                if (message != null && !read(message).expiresAt().isAfter(now)) { target.delete(message); deleted++; }
                expiry.delete(row);
            }
            return deleted;
        });
    }
    /**
     * 固定历史目标，不公开表或仓库查询参数。
     * @param partition 完整业务边界摘要
     * @return 目标事务 DTO
     */
    private IEasyDataTransactionDto history(String partition) { return data.get("history:" + partition).atomic(); }
    /** @return 本历史域的到期索引事务 DTO */
    private IEasyDataTransactionDto expiry() { return data.get("expiry").atomic(); }
    /**
     * 校验显式结构和物理目标，损坏数据不伪装成历史不存在。
     * @param row 当前 DTO
     * @return 原始存储正文
     */
    private Stored read(EasyDataRow row) {
        try {
            var value = mapper.readValue(row.getValue(), Stored.class);
            if (!row.getTarget().equals("history:" + partition(value.reference(), value.personality()))) { throw new IllegalArgumentException(); }
            return value;
        } catch (RuntimeException invalid) { throw new IllegalStateException("历史数据版本、正文或来源边界无效。"); }
    }
    /**
     * 包含完整引用及人格身份，不使用不可恢复的随机内存 epoch。
     * @param reference 来源引用
     * @param personality 人格身份
     * @return 稳定分区键
     */
    private String partition(MemoryNamespaceReference reference, String personality) { return digest(mapper.writeValueAsString(List.of(reference, personality))); }
    /**
     * 为索引生成固定长度键，保留 UTF-16 每个字符的完整身份。
     * @param value 原值
     * @return 六十四位摘要
     */
    private String digest(String value) { return org.zexnocs.teanekocore.utils.StableDigest.sha256(value); }
    /**
     * 有符号秒翻转最高位后按无符号十六进制排序，纳秒固定宽度，支持纪元前时刻。
     * @param instant 原时刻
     * @return 与时间顺序一致的固定宽度键
     */
    private String order(Instant instant) {
        return HexFormat.of().formatHex(ByteBuffer.allocate(12).putLong(instant.getEpochSecond() ^ Long.MIN_VALUE).putInt(instant.getNano()).array());
    }
    /**
     * 固定同消息和到期时间的索引位置，跨空间不会相互覆盖。
     * @param entry 到期索引
     * @return 有界索引键
     */
    private String indexKey(Expiry entry) { return order(entry.expiresAt()) + "-" + entry.partition() + "-" + entry.key(); }
    /**
     * 复用专用 EasyData 的提交后完成机制，不另建事务框架。
     * @param action 事务工作
     * @param <T> 结果类型
     * @return 实际提交后的结果
     */
    private <T> CompletionStage<T> submit(Supplier<T> action) {
        try { return data.transaction("聊天历史事务", action); }
        catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 一个消息的显式存储结构，过期时间与正文同事务保存。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param schemaVersion 结构版本
     * @param reference 真实边界
     * @param personality 人格身份
     * @param message 消息
     * @param expiresAt 到期时刻
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private record Stored(int schemaVersion, MemoryNamespaceReference reference, String personality, HistoryMessage message, Instant expiresAt) {
        /**
         * 拒绝未知版本或缺失来源，不能在坏数据上继续分页。
         * @param schemaVersion 版本
         * @param reference 原引用
         * @param personality 人格身份
         * @param message 原消息
         * @param expiresAt 到期时刻
         * @throws IllegalArgumentException 当版本或边界无效时
         */
        private Stored {
            if (schemaVersion != 1 || reference == null || personality == null || personality.isBlank() || message == null || expiresAt == null
                    || !reference.scope().equals(message.source().scope())) { throw new IllegalArgumentException("历史存储结构无效。"); }
        }
    }
    /**
     * 独立到期目标的不可变引用，不复制整条历史正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param partition 历史分区
     * @param key 消息键
     * @param expiresAt 到期时刻
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private record Expiry(String partition, String key, Instant expiresAt) {}
}
