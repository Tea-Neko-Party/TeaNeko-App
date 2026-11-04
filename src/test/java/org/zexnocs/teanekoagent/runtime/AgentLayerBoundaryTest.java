package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Agent 运行域直接装配 App 集成、具体配置或数据库实现，并保留专用存储边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：区分运行域约束与全项目分层，避免将整个组件目录误称为纯数据。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class AgentLayerBoundaryTest {
    /**
     * 业务 Store 必须经专用 EasyData DTO 访问，仓库和 JPA 只存在于专用数据基础设施。
     * @throws Exception 当源码无法读取时
     */
    @Test void agentPersistenceDoesNotBypassEasyData() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent"); var violations = new ArrayList<String>();
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (root.relativize(file).toString().replace('\\', '/').startsWith("database/easydata/")) { continue; }
                for (var line : Files.readAllLines(file)) {
                    var text = line.strip();
                    if (text.startsWith("import ") && (text.contains("Repository") || text.contains("jakarta.persistence")
                            || text.contains(".easydata.general.") || text.contains("BaseEasyDataObject") || text.contains("Pageable"))) {
                        violations.add(root.relativize(file) + ": " + text);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "发现 Agent 数据封装被绕过：" + violations);
    }

    /**
     * 验证运行域只使用 Core 与组件端口，不直接装配 App 集成、配置及具体存储。
     * 2026/11/05：组件目录已包含完整能力实现，不再将其整体视为纯数据。
     * @throws Exception 当源码无法读取时
     */
    @Test void agentDomainDoesNotImportPlatformLegacyOrAdapters() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent/runtime");
        var violations = new ArrayList<String>();
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (var line : Files.readAllLines(file)) {
                    if (line.stripLeading().startsWith("import org.zexnocs.")
                            && !line.contains("org.zexnocs.teanekoagent.runtime.")
                            && !line.contains("org.zexnocs.teanekoagent.component.")
                            && !line.contains("org.zexnocs.teanekocore.")) {
                        violations.add(root.relativize(file) + ": " + line);
                    }
                    if (line.stripLeading().startsWith("import org.zexnocs.teanekoagent.component.")
                            && (line.contains(".adapter.") || line.contains(".configuration.")
                            || line.contains(".file_config.") || line.contains(".EasyData"))) {
                        violations.add(root.relativize(file) + ": " + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "发现 Agent 领域反向依赖：" + violations);
    }
}
