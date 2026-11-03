package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Agent 实例与业务边界反向依赖 App、旧实现或具体配置/数据库适配器。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
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
     * 验证运行领域只依赖自身、LLM、Core 及明确的不可变记忆引用，不放行整个记忆包或具体存储。
     * 平台和数据库适配仍保留在 adapter 或装配目录，能力接入不能扩大基础设施依赖。
     * @throws Exception 当源码无法读取时
     */
    @Test void agentDomainDoesNotImportPlatformLegacyOrAdapters() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent/agent");
        var violations = new ArrayList<String>();
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (var line : Files.readAllLines(file)) {
                    if (line.stripLeading().startsWith("import org.zexnocs.")
                            && !line.contains("org.zexnocs.teanekoagent.agent.")
                            && !line.contains("org.zexnocs.teanekoagent.llm.")
                            && !line.strip().equals("import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;")
                            && !line.contains("org.zexnocs.teanekocore.")) {
                        violations.add(root.relativize(file) + ": " + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "发现 Agent 领域反向依赖：" + violations);
    }
}
