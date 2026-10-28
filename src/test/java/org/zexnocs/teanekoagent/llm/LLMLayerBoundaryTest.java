package org.zexnocs.teanekoagent.llm;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包级依赖约束，防止新内容/适配器重新把 App 或上层 Agent 类型带入 LLM。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMLayerBoundaryTest {
    /**
     * 验证LLM 生产代码仅依赖本层及 Core，防止平台或上层业务反向耦合。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void llmProductionSourcesDoNotImportAppClientPluginOrAgentBusinessTypes() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent/llm");
        var violations = new ArrayList<String>();
        try (var sources = Files.walk(root)) {
            for (var path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (var line : Files.readAllLines(path)) {
                    if (line.stripLeading().startsWith("import org.zexnocs.")
                            && !line.contains("org.zexnocs.teanekoagent.llm.") && !line.contains("org.zexnocs.teanekocore.")) {
                        violations.add(root.relativize(path) + ": " + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Invalid LLM dependencies: " + violations);
    }
}
