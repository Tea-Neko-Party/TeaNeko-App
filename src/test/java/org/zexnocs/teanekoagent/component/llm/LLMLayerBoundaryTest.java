package org.zexnocs.teanekoagent.component.llm;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 底层包依赖约束，防止协议、模型和配置装配依赖 App 或上层 Agent 集成。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：明确协议底层目录，排除根目录的 ECS 集成并校验全限定类型引用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class LLMLayerBoundaryTest {
    /**
     * 验证 LLM 四个底层目录只依赖彼此及 Core，不放行根目录组件和上层业务。
     * 2026/11/05：用精确子包范围区分纯模型底层与组件集成，覆盖内联全限定类型。
     *
     * @throws Exception 当生产源码目录无法遍历或读取时
     */
    @Test
    void llmProductionSourcesDoNotImportAppClientPluginOrAgentBusinessTypes() throws Exception {
        var root = Path.of("src/main/java/org/zexnocs/teanekoagent/component/llm");
        var packages = List.of("framework", "instance", "file_config", "configuration");
        var prefixes = packages.stream().map(name -> "org.zexnocs.teanekoagent.component.llm." + name + ".")
                .toList();
        var reference = Pattern.compile("org\\.zexnocs\\.[\\w.]+");
        var violations = new ArrayList<String>();
        for (var name : packages) {
            try (var sources = Files.walk(root.resolve(name))) {
                for (var path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var code = Files.readString(path).replaceAll("(?m)^package\\s+[^;]+;", "")
                            .replaceAll("(?s)/\\*.*?\\*/", "")
                            .replaceAll("(?m)^\\s*//.*$", "");
                    var matches = reference.matcher(code);
                    while (matches.find()) {
                        String dependency = matches.group();
                        if (!dependency.startsWith("org.zexnocs.teanekocore.")
                                && prefixes.stream().noneMatch(dependency::startsWith)) {
                            violations.add(root.relativize(path) + ": " + dependency);
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "LLM 底层存在越层依赖：" + violations);
    }
}
