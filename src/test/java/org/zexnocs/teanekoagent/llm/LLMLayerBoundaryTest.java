package org.zexnocs.teanekoagent.llm;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立 LLM 子模块依赖约束，防止协议、模型、配置与管理入口反向依赖 Agent 运行域。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：明确协议底层目录，排除根目录的 ECS 集成并校验全限定类型引用。
 * <br>2026/11/07：覆盖整个独立 LLM 子模块，仅允许自身、App 和 Core 依赖。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
class LLMLayerBoundaryTest {
    /**
     * 2026/11/07：验证整个 LLM 子模块的引用，包括管理入口与内联全限定类型。
     *
     * @throws Exception 当生产源码目录无法遍历或读取时
     */
    @Test
    void llmProductionSourcesDependOnlyOnSelfAppAndCore() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent/llm");
        var reference = Pattern.compile("org\\.zexnocs\\.[\\w.]+");
        var violations = new ArrayList<String>();
        try (var sources = Files.walk(root)) {
            var paths = sources.filter(path -> path.toString().endsWith(".java")).toList();
            assertTrue(!paths.isEmpty(), "独立 LLM 子模块必须存在可核对的生产源码。");
            for (var path : paths) {
                var code = Files.readString(path).replaceAll("(?m)^package\\s+[^;]+;", "")
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("(?m)^\\s*//.*$", "");
                var matches = reference.matcher(code);
                while (matches.find()) {
                    String dependency = matches.group();
                    if (!dependency.startsWith("org.zexnocs.teanekocore.")
                            && !dependency.startsWith("org.zexnocs.teanekoapp.")
                            && !dependency.startsWith("org.zexnocs.teanekoagent.llm.")) {
                        violations.add(root.relativize(path) + ": " + dependency);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "LLM 底层存在越层依赖：" + violations);
    }
}
