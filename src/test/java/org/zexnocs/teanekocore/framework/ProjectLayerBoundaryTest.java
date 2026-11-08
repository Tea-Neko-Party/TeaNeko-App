package org.zexnocs.teanekocore.framework;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定项目单向依赖及单工具提供者约定，覆盖 import、全限定类型与反射类名。
 * 不加载业务 Bean，不连接数据库或平台。
 *
 * <br>2026/11/08：检查实际类型和包导入引用，纯包名的归属约束不作为反向类型依赖。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
class ProjectLayerBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/org/zexnocs");
    private static final List<String> LAYERS = List.of(
            "teanekocore", "teanekoapp", "teanekoagent", "teanekoclient", "teanekoplugin");
    private static final Pattern REFERENCE = Pattern.compile(
            "org\\.zexnocs\\.(teanekocore|teanekoapp|teanekoagent|teanekoclient|teanekoplugin)\\b(?=\\.[\\w@*])");
    private static final Pattern TOOL = Pattern.compile("@TeaNekoAgentTool\\s*\\(");

    /**
     * 任一较低层不能通过导入、内联全限定名或反射类名绑定较高层。
     * @throws Exception 当生产源码无法读取时
     */
    @Test
    void productionDependenciesPointOnlyDownward() throws Exception {
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(ROOT)) {
            for (var file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                var relative = ROOT.relativize(file);
                String layer = relative.getName(0).toString();
                int rank = LAYERS.indexOf(layer);
                if (rank < 0) {
                    continue;
                }
                var matcher = REFERENCE.matcher(code(file));
                while (matcher.find()) {
                    if (LAYERS.indexOf(matcher.group(1)) > rank) {
                        violations.add(relative + " → " + matcher.group(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "发现反向项目依赖：" + violations);
    }

    /**
     * 单独的工具提供者可以独立注册和禁用，共享业务通过服务而非聚合注解类复用。
     * @throws Exception 当生产源码无法读取时
     */
    @Test
    void annotatedToolsHaveIndependentProviders() throws Exception {
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(ROOT)) {
            for (var file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (TOOL.matcher(code(file)).results().count() > 1) {
                    violations.add(ROOT.relativize(file).toString());
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "工具提供者仍聚合多个工具方法：" + violations);
    }

    /**
     * 去除说明注释但保留字符串，防止漏检反射类名与内联全限定引用。
     * @param file 当前源码文件
     * @return 不含块注释及独占行注释的源码
     * @throws Exception 当文件读取失败时
     */
    private static String code(Path file) throws Exception {
        return Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }
}
