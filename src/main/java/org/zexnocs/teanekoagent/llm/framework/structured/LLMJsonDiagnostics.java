package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.utils.PrivateFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/**
 * 可选失败原文归档：默认关闭，只写所有者可读目录，不将原文带入日志或 Web。
 * <br>2026/11/06：每份最多 262144 字符，最多 20 份，写入时清理超过一天的本类文件。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Component
public final class LLMJsonDiagnostics {
    private static final Logger LOG = LoggerFactory.getLogger(LLMJsonDiagnostics.class);
    private final boolean enabled;
    private final Path directory;

    /**
     * @param enabled 显式本地启动配置，默认禁止保存可能含隐私的原文
     * @param directory 本机诊断目录，不属于静态资源或素材目录
     */
    public LLMJsonDiagnostics(@Value("${tea-neko.llm.json-diagnostics.enabled:false}") boolean enabled,
            @Value("${tea-neko.llm.json-diagnostics.directory:data/diagnostics/llm-json}") String directory) {
        this.enabled = enabled;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    /**
     * 只在显式启用时记录失败原文；归档失败不能掩盖原来的 JSON 错误。
     * @param request 原请求编号
     * @param text 原输出，不进行内容修复
     */
    public synchronized void record(UUID request, String text) {
        if (!enabled) { return; }
        try {
            PrivateFiles.rejectLinks(directory);
            Files.createDirectories(directory);
            PrivateFiles.protect(directory, true);
            prune();
            var file = directory.resolve("output-" + request + "-" + UUID.randomUUID() + ".txt");
            Files.createFile(file);
            PrivateFiles.protect(file, false);
            var bounded = text.length() <= 262144 ? text : text.substring(0, 262144) + "\n[容量超限，后续未保存]";
            Files.writeString(file, bounded, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            LOG.warn("JSON 失败原文已保存到受限本机目录。请求={}，文件={}；该文件可能含隐私，请勿公开。", request, file.getFileName());
        } catch (IOException | SecurityException failure) {
            LOG.warn("JSON 失败原文无法安全保存。请求={}，异常类型={}；仍报告原解析失败。", request, failure.getClass().getName());
        }
    }

    /**
     * 仅清理本类命名的普通文件，不递归、不跟随链接；保留空间给下一份输出。
     * @throws IOException 当受限目录不可读取或清理时
     */
    private void prune() throws IOException {
        try (var files = Files.list(directory)) {
            var owned = files.filter(path -> path.getFileName().toString().matches("output-[0-9a-f-]{36}-[0-9a-f-]{36}\\.txt"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> {
                        try { return new Entry(path, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()); }
                        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    }).sorted(Comparator.comparing(Entry::time).reversed()).toList();
            var cutoff = Instant.now().minusSeconds(86400);
            for (int index = 0; index < owned.size(); index++) {
                if (index >= 19 || owned.get(index).time().isBefore(cutoff)) {
                    Files.delete(owned.get(index).path());
                }
            }
        } catch (java.io.UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /**
     * 有限归档目录的一次扫描结果。
     * @param path 本类拥有的文件
     * @param time 文件时间
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record Entry(Path path, Instant time) { }
}
