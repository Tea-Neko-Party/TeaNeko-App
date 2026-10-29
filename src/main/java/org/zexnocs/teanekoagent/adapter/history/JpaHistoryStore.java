package org.zexnocs.teanekoagent.adapter.history;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.history.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 复用数据库任务提交独立历史表；索引分页和显式 schema 不依赖内存会话存活。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class JpaHistoryStore implements IHistoryStore {
    private final HistoryRepository repository;
    private final IDatabaseService database;
    private final ObjectMapper mapper;
    /**
     * 保存事务设施和转换器，不执行数据库访问。
     * @param repository 独立行仓库
     * @param database 原事务入口
     * @param mapper JSON 转换器
     */
    public JpaHistoryStore(HistoryRepository repository, IDatabaseService database, ObjectMapper mapper) {
        this.repository = Objects.requireNonNull(repository, "历史仓库不能为空。"); this.database = Objects.requireNonNull(database, "历史事务入口不能为空。");
        this.mapper = mapper.rebuild().disable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE).build();
    }
    /**
     * 同来源重投不延长保留期，正文冲突拒绝且不覆盖原记录。
     * @param context 原边界
     * @param message 原消息
     * @param expiresAt 到期时间
     * @return 已提交新增结果
     */
    @Override public CompletionStage<Boolean> append(HistoryContext context, HistoryMessage message, Instant expiresAt) {
        return submit(() -> {
            var partition = partition(context); var key = digest(mapper.writeValueAsString(List.of(message.role(), message.messageId())));
            var old = repository.findByPartitionAndMessageKey(partition, key);
            if (old != null) {
                if (!read(old, context).sameContent(message)) { throw new IllegalArgumentException("同一历史消息来源对应不同正文。"); }
                return false;
            }
            var row = new HistoryDataObject(); row.setPartition(partition); row.setMessageKey(key); row.setOccurredAt(message.occurredAt()); row.setExpiresAt(expiresAt);
            row.setPayload(mapper.writeValueAsString(new Stored(1, context.reference(), context.personality().identity(), message)));
            repository.saveAndFlush(row); return true;
        });
    }
    /**
     * 每次最多扫描二百行，字符不足整条省略且继续游标仍可推进。
     * @param context 当前边界
     * @param query 查询条件
     * @param now 固定查询时刻
     * @return 有界完整消息及继续位置
     */
    @Override public CompletionStage<HistoryPage> search(HistoryContext context, HistoryQuery query, Instant now) {
        return submit(() -> {
            var items = new ArrayList<HistoryMessage>(); long cursor = query.before(); int scanned = 0; boolean omitted = false;
            while (scanned < 200 && items.size() < query.limit()) {
                var rows = repository.findByPartitionAndIdLessThanAndExpiresAtGreaterThanOrderByIdDesc(partition(context), cursor, now, PageRequest.of(0, Math.min(32, 200 - scanned)));
                if (rows.isEmpty()) { cursor = 0; break; }
                for (var row : rows) {
                    long id = Objects.requireNonNull(row.getId(), "历史行序号不能为空。");
                    if (id <= 0 || id >= cursor || !partition(context).equals(row.getPartition()) || !row.getExpiresAt().isAfter(now)) {
                        throw new IllegalStateException("历史页面定位或顺序无效。");
                    }
                    cursor = id; scanned++; var message = read(row, context);
                    if (query.start() != null && message.occurredAt().isBefore(query.start()) || query.end() != null && message.occurredAt().isAfter(query.end())
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
     * 只删除一个到期批次，清理延迟不影响查询时的有效期过滤。
     * @param now 当前时刻
     * @return 本批删除量
     */
    @Override public CompletionStage<Integer> prune(Instant now) {
        return submit(() -> {
            var rows = repository.findByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(now, PageRequest.of(0, 100));
            repository.deleteAllInBatch(rows); return rows.size();
        });
    }
    /**
     * 持久化边界来自完整引用及人格身份，随机内存 epoch 不参与重启查询。
     * @param context 已授权上下文
     * @return 固定长度分区摘要
     */
    private String partition(HistoryContext context) { return digest(mapper.writeValueAsString(List.of(context.reference(), context.personality().identity()))); }
    /**
     * 使用每个 Java 字符的完整编码，避免非法代理项的替换导致不同标识合并。
     * @param value 原标识
     * @return 摘要键
     */
    private static String digest(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); digest.update((byte) (c >>> 8)); digest.update((byte) c); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ignored) { throw new IllegalStateException("历史摘要算法不可用。"); }
    }
    /**
     * 验证 schema 和完整分区正文，摘要不能代替业务归属检查。
     * @param row 原行
     * @param context 当前边界
     * @return 已验证消息
     */
    private HistoryMessage read(HistoryDataObject row, HistoryContext context) {
        var stored = mapper.readValue(row.getPayload(), Stored.class);
        if (stored.schemaVersion() != 1 || !context.reference().equals(stored.reference()) || !context.personality().identity().equals(stored.personality())
                || stored.message() == null || !context.reference().scope().equals(stored.message().source().scope()) || !row.getOccurredAt().equals(stored.message().occurredAt())) {
            throw new IllegalStateException("历史数据版本或来源边界无效。");
        }
        return stored.message();
    }
    /**
     * 等待原事务及重试完成，不把排队成功当作写入成功。
     * @param action 当前事务操作
     * @param <T> 返回类型
     * @return 提交后完成的结果
     */
    private <T> CompletionStage<T> submit(Supplier<T> action) {
        try {
            var result = new AtomicReference<T>(); var task = new DatabaseTaskConfig(database, "聊天历史事务");
            task.addTransactionTask(() -> { result.set(null); result.set(action.get()); });
            return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get(), "历史事务缺少结果。"));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 明确持久化 schema，不保存整个人格或会话实体。
     * @param schemaVersion 协议版本
     * @param reference 原始引用
     * @param personality 人格身份
     * @param message 消息正文及来源
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Stored(int schemaVersion, MemoryNamespaceReference reference, String personality, HistoryMessage message) {}
}
