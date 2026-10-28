package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Agent 实例与业务边界反向依赖 App、旧实现或具体配置/数据库适配器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentLayerBoundaryTest {
    /**
     * 验证领域生产代码只依赖 Agent 自身、LLM 和 Core，平台适配保留在 adapter 或装配目录。
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
                            && !line.contains("org.zexnocs.teanekocore.")) {
                        violations.add(root.relativize(file) + ": " + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "发现 Agent 领域反向依赖：" + violations);
    }
}
