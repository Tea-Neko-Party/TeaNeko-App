package org.zexnocs.teanekoagent.integration.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在业务插件启动前迁移已持久化的组件装配，保留原配置身份、秘密和未涉及字段。
 * <br>2026/11/07：使用原 EasyData 事务与写锁分页升级，冲突记录保持原文并明确报告。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
@Order(750)
public final class AgentAssemblyUpgrade implements ApplicationRunner {
    private static final ConfigManager MANAGER = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
    private final ConfigDataRegisterService registration;
    private final ConfigEditService edits;
    private final AgentComponentRegistry components;
    private final ObjectMapper mapper;
    private final ILogger logger;

    /**
     * 借用已初始化的组件目录和原事务入口，不依赖业务插件启用状态。
     * @param registration 原配置 DTO 入口
     * @param edits 原有界配置目录
     * @param components 完整安装组件及兼容转换
     * @param mapper 原 JSON 转换器
     * @param logger 项目日志
     */
    public AgentAssemblyUpgrade(ConfigDataRegisterService registration, ConfigEditService edits,
                                AgentComponentRegistry components, ObjectMapper mapper, ILogger logger) {
        this.registration = registration;
        this.edits = edits;
        this.components = components;
        this.mapper = mapper;
        this.logger = logger;
    }

    /**
     * 分页等待实际提交；数据库错误阻止启动，配置冲突只保留对应原行。
     * @param arguments 宿主参数，不接受外部迁移覆盖指令
     * @throws Exception 当数据库查询或事务在期限内未完成时
     */
    @Override
    public void run(@NonNull ApplicationArguments arguments) throws Exception {
        String after = "";
        do {
            var page = edits.keys(MANAGER, after, 100).toCompletableFuture().get(30, TimeUnit.SECONDS);
            migrate(page.keys()).toCompletableFuture().get(30, TimeUnit.SECONDS);
            after = page.next();
        } while (!after.isEmpty());
    }

    /**
     * 在持有原行写锁时读取最新正文，避免迁移覆盖并发配置操作。
     * @param keys 从宿主配置目录取得的一页真实键
     * @return 实际提交并报告冲突后的阶段
     */
    public CompletionStage<Void> migrate(List<String> keys) {
        var source = registration.getDto(MANAGER);
        var conflicts = new AtomicReference<List<String>>(List.of());
        var task = source.getTaskConfig("Agent 组件装配升级");
        task.addTransactionTask(() -> {
            var rejected = new ArrayList<String>();
            var transaction = source.atomic();
            for (var key : keys) {
                var row = transaction.find(key, EasyDataLock.WRITE);
                if (row == null) {
                    continue;
                }
                final String next;
                try {
                    next = normalize(row.getValue());
                } catch (IllegalArgumentException | JacksonException invalid) {
                    // 2026/11/07：配置正文和异常文本可能包含秘密，只报告稳定目标摘要。
                    rejected.add(StableDigest.sha256(key));
                    continue;
                }
                if (!next.equals(row.getValue())) {
                    row.setValue(next);
                    transaction.save(row);
                }
            }
            conflicts.set(List.copyOf(rejected));
        });
        return task.pushAsync().thenRun(() -> conflicts.get().forEach(digest -> logger.warn(
                "Agent 配置升级", "组件装配存在冲突或非法参数，已保留原配置；目标摘要：" + digest)));
    }

    /**
     * 只替换组件字段，旧平铺参数转换为完整原装配，未知顶层字段保持原文。
     * @param value 原行完整正文
     * @return 需要升级时的新正文，否则为原文
     * @throws IllegalArgumentException 当配置结构或组件转换存在冲突时
     */
    private String normalize(String value) {
        if (!(mapper.readTree(value) instanceof ObjectNode document)) {
            throw new IllegalArgumentException("Agent 配置必须为对象。");
        }
        var assembly = document.get("components");
        if (assembly == null || assembly.isNull()) {
            // 2026/11/07：在开放编辑前固定旧完整装配，停用期间保存不能丢掉隐藏的历史选择。
            document.put("components", components.normalizeConfiguration(value));
            return mapper.writeValueAsString(document);
        }
        if (!assembly.isString()) {
            throw new IllegalArgumentException("Agent 组件装配必须使用原字符串配置格式。");
        }
        String current = assembly.asString();
        String migrated = components.normalize(current);
        if (mapper.readTree(current).equals(mapper.readTree(migrated))) {
            return value;
        }
        document.put("components", migrated);
        return mapper.writeValueAsString(document);
    }
}
