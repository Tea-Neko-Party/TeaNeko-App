package org.zexnocs.teanekoapp.web.console;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 指令台的持久认领与回执，业务只使用原 EasyData DTO，不依赖 Repository。
 * 账本不保存命令正文或任意处理方法返回值。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class ConsoleOperationStore {
    private final IEasyDataDto dto;
    private final ObjectMapper mapper;

    /**
     * @param data 原 EasyData 服务，实体与域由本地固定
     * @param mapper 原存储转换器
     */
    @Autowired
    public ConsoleOperationStore(IEasyDataService data, ObjectMapper mapper) {
        this(data.getEasyDataFor(GeneralEasyDataObject.class, "web.console.v1", "operations"), mapper);
    }

    /**
     * @param dto 宿主固定域 DTO，测试可借原事务夹具
     * @param mapper 原转换器
     */
    public ConsoleOperationStore(IEasyDataDto dto, ObjectMapper mapper) {
        this.dto = dto;
        this.mapper = mapper;
    }

    /**
     * @param actor 宿主主体
     * @param target 宿主固定的客户端、账号和目标摘要
     * @param digest 完整原请求摘要
     * @param request 原请求
     * @param instance 本次进程标识
     * @param guard 实际事务内授权
     * @return 只有本次取得执行权时 acquired 为真
     */
    public CompletionStage<Claim> claim(String actor, String target, String digest, ClientConsole.Request request,
                                         UUID instance, Runnable guard) {
        return transaction(() -> {
            guard.run();
            var row = dto.atomic().find("journal", EasyDataLock.WRITE);
            var journal = row == null ? new LinkedHashMap<UUID, Entry>() : read(row.getValue());
            var now = Instant.now();
            journal.values().removeIf(entry -> entry.receipt().at().plusSeconds(86400).isBefore(now));
            var previous = journal.get(request.operationId());
            if (previous != null) {
                verify(previous, actor, target);
                if (!previous.digest().equals(digest)) {
                    throw new IllegalArgumentException("指令操作编号已绑定其他正文或连接版本。");
                }
                return new Claim(false, previous.receipt());
            }
            if (request.observedAt().isBefore(now.minusSeconds(300)) || request.observedAt().isAfter(now.plusSeconds(30))) {
                throw new IllegalStateException("指令目录已过期，请刷新连接状态后明确创建新操作。");
            }
            if (journal.size() >= 256) {
                throw new IllegalStateException("指令台回执窗口已满，请等待旧回执过期。");
            }
            var receipt = new ClientConsole.Receipt(request.operationId(), "ACCEPTED", "", List.of(), List.of(),
                    "原请求已持久受理，尚未证明命令启动或平台发送。", now);
            journal.put(request.operationId(), new Entry(StableDigest.sha256(actor), target, digest, instance, receipt));
            guard.run();
            save(row, journal);
            return new Claim(true, receipt);
        });
    }

    /**
     * @param actor 当前主体
     * @param target 固定目标摘要
     * @param operation 原操作
     * @param status 真实新阶段
     * @param commandStatus 原分发状态
     * @param output 明确输出
     * @param messages 实际已确认消息号
     * @param note 固定事实说明
     * @param guard 当前授权或宿主仅保存事实的生命周期检查
     * @return 原提交时间不变的实际回执
     */
    public CompletionStage<ClientConsole.Receipt> finish(String actor, String target, UUID operation, String status,
            String commandStatus, List<String> output, List<String> messages, String note, Runnable guard) {
        return transaction(() -> {
            guard.run();
            var row = dto.atomic().find("journal", EasyDataLock.WRITE);
            if (row == null) {
                throw new NoSuchElementException("指令台原操作不在保留窗口。");
            }
            var journal = read(row.getValue());
            var entry = journal.get(operation);
            if (entry == null) {
                throw new NoSuchElementException("指令台原操作不在保留窗口。");
            }
            verify(entry, actor, target);
            if (!Set.of("ACCEPTED", "RUNNING").contains(entry.receipt().status())) {
                return entry.receipt();
            }
            var receipt = new ClientConsole.Receipt(operation, status, commandStatus, output, messages, note, entry.receipt().at());
            journal.put(operation, new Entry(entry.actor(), target, entry.digest(), entry.instance(), receipt));
            save(row, journal);
            return receipt;
        });
    }

    /**
     * @param actor 真实主体
     * @param target 固定目标摘要
     * @param operation 原编号
     * @param current 当前进程
     * @param active 在真实事务中查询当前工作拥有者，避免认领与查询之间误判
     * @param guard 当前查询授权
     * @return 原回执；失去工作拥有者的非终态明确标为不确定，禁止重放
     */
    public CompletionStage<ClientConsole.Receipt> operation(String actor, String target, UUID operation,
            UUID current, Supplier<Boolean> active, Runnable guard) {
        return transaction(() -> {
            guard.run();
            var row = dto.atomic().find("journal", EasyDataLock.WRITE);
            var journal = row == null ? new LinkedHashMap<UUID, Entry>() : read(row.getValue());
            var entry = journal.get(operation);
            if (entry == null || entry.receipt().at().plusSeconds(86400).isBefore(Instant.now())) {
                throw new NoSuchElementException("原指令回执不存在或已过期。");
            }
            verify(entry, actor, target);
            if (Set.of("ACCEPTED", "RUNNING").contains(entry.receipt().status())
                    && (!entry.instance().equals(current) || !active.get())) {
                var receipt = new ClientConsole.Receipt(operation, "INDETERMINATE", "OWNER_LOST", entry.receipt().output(),
                        entry.receipt().confirmedMessages(), "原进程或工作拥有者已丢失，实际副作用未确认，不会自动重放。", entry.receipt().at());
                journal.put(operation, new Entry(entry.actor(), target, entry.digest(), entry.instance(), receipt));
                guard.run();
                save(row, journal);
                return receipt;
            }
            return entry.receipt();
        });
    }

    /**
     * @param entry 原绑定
     * @param actor 当前主体
     * @param target 当前目标摘要
     * @throws SecurityException 当主体或目标不匹配时
     */
    private static void verify(Entry entry, String actor, String target) {
        if (!entry.actor().equals(StableDigest.sha256(actor)) || !entry.target().equals(target)) {
            throw new SecurityException("不能查询其他主体或目标的指令操作。");
        }
    }

    /**
     * @param text 本域固定格式
     * @return 独立可变事务候选
     */
    private LinkedHashMap<UUID, Entry> read(String text) {
        return new LinkedHashMap<>(mapper.readValue(text, Journal.class).entries());
    }

    /**
     * @param row 原行，缺失时创建
     * @param journal 本次事务候选
     */
    private void save(org.zexnocs.teanekocore.database.easydata.core.@org.jspecify.annotations.Nullable EasyDataRow row,
            Map<UUID, Entry> journal) {
        String value = mapper.writeValueAsString(new Journal(journal));
        if (row == null) {
            row = dto.atomic().create("journal", value);
        } else {
            row.setValue(value);
        }
        dto.atomic().save(row);
    }

    /**
     * @param work 仅数据库状态转换，不执行平台或命令
     * @param <T> 非空结果
     * @return 实际事务提交后的阶段
     */
    private <T> CompletionStage<T> transaction(Supplier<T> work) {
        var result = new AtomicReference<T>();
        var task = dto.getTaskConfig("指令台回执事务");
        task.addTransactionTask(() -> {
            result.set(null);
            result.set(Objects.requireNonNull(work.get()));
        });
        return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get())).toCompletableFuture().minimalCompletionStage();
    }

    /**
     * 一次真实执行权和原受理结果。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param acquired 是否本次新认领
     * @param receipt 已提交回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Claim(boolean acquired, ClientConsole.Receipt receipt) {}

    /**
     * 私有绑定，不保存正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param actor 主体摘要
     * @param target 目标摘要
     * @param digest 完整原请求摘要
     * @param instance 原拥有进程
     * @param receipt 原结果
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Entry(String actor, String target, String digest, UUID instance, ClientConsole.Receipt receipt) {}

    /**
     * 同事务持久窗口。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param entries 原操作映射
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Journal(Map<UUID, Entry> entries) {}
}
