import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 对 Git 新改动执行 Java 语法树格式检查，保留历史代码基线，不通过全仓重排规避问题。
 * <br>2026/11/07：检查真实乱码、缩进、同一行多语句、密集块和控制分支的大括号。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
public final class JavaChangeCheck {
    /**
     * 不创建静态工具实例。
     */
    private JavaChangeCheck() {
    }

    /**
     * @param args 可选 Git 对比基线，默认 HEAD；CI 可指定合并基线
     * @throws Exception Git、源码读取或质量检查失败时
     */
    public static void main(String[] args) throws Exception {
        String base = args.length == 0 ? "HEAD" : args[0];
        var changes = changes(base);
        var errors = new LinkedHashSet<String>();
        for (var entry : changes.entrySet()) {
            var path = Path.of(entry.getKey());
            if (!Files.isRegularFile(path)) {
                continue;
            }
            var lines = Files.readAllLines(path);
            for (int line : entry.getValue()) {
                if (line > lines.size()) {
                    continue;
                }
                String text = lines.get(line - 1);
                if (text.indexOf('\ufffd') >= 0 || text.matches("^\\s*\\t.*") || !text.equals(text.stripTrailing())) {
                    errors.add(path + ":" + line + " 新改动含乱码、Tab 缩进或行尾空白。");
                }
            }
            inspect(path, entry.getValue(), errors);
        }
        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            throw new IllegalStateException("新增 Java 格式违约共 " + errors.size() + " 处；历史未改动行不计入。");
        }
        System.out.println("Java 新改动检查通过，文件数：" + changes.size());
    }

    /**
     * @param base Git 对比引用
     * @return 修改和未跟踪 Java 文件的新增行号
     * @throws Exception Git 读取失败时
     */
    private static Map<String, Set<Integer>> changes(String base) throws Exception {
        var result = new LinkedHashMap<String, Set<Integer>>();
        String file = null;
        int line = 0;
        var header = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");
        for (String text : git("-c", "core.quotePath=false", "diff", "--unified=0", base, "--", "*.java").split("\\R")) {
            if (text.startsWith("+++ b/")) {
                file = text.substring(6);
            } else if (text.startsWith("@@")) {
                var match = header.matcher(text);
                if (match.matches()) {
                    line = Integer.parseInt(match.group(1));
                }
            } else if (file != null && text.startsWith("+")) {
                result.computeIfAbsent(file, ignored -> new HashSet<>()).add(line++);
            } else if (!text.startsWith("-") && !text.startsWith("\\")) {
                line++;
            }
        }
        for (String added : git("ls-files", "--others", "--exclude-standard", "--", "*.java").split("\\R")) {
            if (!added.isBlank()) {
                var lines = result.computeIfAbsent(added, ignored -> new HashSet<>());
                int count = Files.readAllLines(Path.of(added)).size();
                for (int i = 1; i <= count; i++) {
                    lines.add(i);
                }
            }
        }
        return result;
    }

    /**
     * 只解析而不做依赖解析，字符串、注释、for 头部和文本块不按分号误判。
     *
     * @param path    当前文件
     * @param changed 本次新增行
     * @param errors  聚合错误
     * @throws Exception 源码无法读取时
     */
    private static void inspect(Path path, Set<Integer> changed, Set<String> errors) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null,
                files.getJavaFileObjects(path));
            var trees = Trees.instance(task);
            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    /**
                     * 定位当前节点在源码中的起始行。
                     * @param tree 当前语法节点
                     * @return 节点起始行
                     */
                    private int line(Tree tree) {
                        return (int) unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, tree));
                    }

                    /**
                     * 只登记新增行的格式问题。
                     * @param tree 发生问题的节点
                     * @param message 固定检查原因
                     */
                    private void report(Tree tree, String message) {
                        int line = line(tree);
                        if (changed.contains(line)) {
                            errors.add(path + ":" + line + " " + message);
                        }
                    }

                    /**
                     * 检查块内语句位置，继续遍历嵌套结构。
                     * @param block 当前方法或分支块
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitBlock(BlockTree block, Void ignored) {
                        int previous = line(block);
                        long previousStart = -1;
                        for (var statement : block.getStatements()) {
                            int current = line(statement);
                            long start = trees.getSourcePositions().getStartPosition(unit, statement);
                            if (current == previous && start != previousStart) {
                                report(statement, "块内语句须独立换行。");
                            }
                            previous = current;
                            previousStart = start;
                        }
                        return super.visitBlock(block, ignored);
                    }

                    /**
                     * 检查控制结构是否显式使用语句块。
                     * @param body 分支主体
                     * @param owner 分支所属节点
                     */
                    private void braces(StatementTree body, Tree owner) {
                        if (!(body instanceof BlockTree)) {
                            report(owner, "控制分支须使用大括号。");
                        }
                    }

                    /**
                     * 校验条件分支，保留连续 else-if 的合法形式。
                     * @param node 条件分支
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitIf(IfTree node, Void ignored) {
                        braces(node.getThenStatement(), node);
                        if (node.getElseStatement() != null && !(node.getElseStatement() instanceof IfTree)) {
                            braces(node.getElseStatement(), node.getElseStatement());
                        }
                        return super.visitIf(node, ignored);
                    }

                    /**
                     * 校验计数循环的主体。
                     * @param node 循环
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitForLoop(ForLoopTree node, Void ignored) {
                        braces(node.getStatement(), node);
                        return super.visitForLoop(node, ignored);
                    }

                    /**
                     * 校验集合遍历的主体。
                     * @param node 遍历循环
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void ignored) {
                        braces(node.getStatement(), node);
                        return super.visitEnhancedForLoop(node, ignored);
                    }

                    /**
                     * 校验条件循环的主体。
                     * @param node 条件循环
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitWhileLoop(WhileLoopTree node, Void ignored) {
                        braces(node.getStatement(), node);
                        return super.visitWhileLoop(node, ignored);
                    }

                    /**
                     * 校验后检查循环的主体。
                     * @param node 后检查循环
                     * @param ignored 不使用的上下文
                     * @return 不返回值
                     */
                    @Override
                    public Void visitDoWhileLoop(DoWhileLoopTree node, Void ignored) {
                        braces(node.getStatement(), node);
                        return super.visitDoWhileLoop(node, ignored);
                    }
                }.scan(unit, null);
            }
        }
    }

    /**
     * @param arguments 无 shell 插值的 Git 参数
     * @return UTF-8 输出
     * @throws Exception Git 查询失败时
     */
    private static String git(String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.addAll(List.of("git", "-c", "core.safecrlf=false"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("无法读取 Git 改动。");
        }
        return output;
    }
}
