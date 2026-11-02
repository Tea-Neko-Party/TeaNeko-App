package org.zexnocs.teanekoagent.adapter.skill;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.skill.ISkillCatalog;
import org.zexnocs.teanekoagent.skill.SkillCatalogSnapshot;
import org.zexnocs.teanekoagent.skill.SkillDefinition;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/**
 * 从本地受控根目录加载一层 SKILL.md，完整验证后原子发布，不执行文件中的代码。
 * 构造无 I/O；首次使用惰性加载，失败重载保留上一完整快照。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class LocalSkillCatalog implements ISkillCatalog {
    private final Environment environment;
    private final ObjectMapper yaml = YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private volatile SkillCatalogSnapshot current;
    /**
     * 保存可信本地配置，不创建目录或自动安装外部文件。
     * @param environment Spring 本地配置
     */
    public LocalSkillCatalog(Environment environment) { this.environment = Objects.requireNonNull(environment); }
    /**
     * 未加载时在同一互斥域建立完整快照，已有快照不重复读盘。
     * @return 当前目录快照
     */
    @Override public SkillCatalogSnapshot snapshot() {
        var value = current;
        if (value != null) { return value; }
        synchronized (this) { return current == null ? reload() : current; }
    }
    /**
     * 先校验全部候选再替换引用；路径、格式或重复错误不发布部分目录。
     * @param guard 发布前授权及取消复核
     * @return 新的完整快照
     * @throws IllegalStateException 当本地目录、编码、格式或范围无效时
     */
    @Override public synchronized SkillCatalogSnapshot reload(Runnable guard) {
        try {
            guard.run();
            var definitions = new ArrayList<SkillDefinition>();
            if (environment.getProperty("tea-neko.agent.skills.builtin-example", Boolean.class, true)) {
                definitions.add(read(new ClassPathResource("agent/skills/clear-explanation/SKILL.md").getInputStream(), "clear-explanation"));
            }
            var root = Path.of(environment.getProperty("tea-neko.agent.skills.directory", "config/agent/skills")).toAbsolutePath().normalize();
            if (Files.exists(root)) {
                root = root.toRealPath();
                if (!Files.isDirectory(root)) { throw new IllegalArgumentException("Skill 根路径必须为目录。"); }
                final List<Path> entries;
                try (var files = Files.list(root)) { entries = files.sorted().limit(129).toList(); }
                if (entries.size() > 128) { throw new IllegalArgumentException("Skill 根目录项目数量过多。"); }
                for (var entry : entries) {
                    if (Files.isSymbolicLink(entry) || !entry.toRealPath().startsWith(root)) { throw new IllegalArgumentException("Skill 目录不能通过链接访问根目录外的内容。"); }
                    if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) { continue; }
                    var file = entry.resolve("SKILL.md");
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { continue; }
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(root)) {
                        throw new IllegalArgumentException("Skill 正文必须为受控根目录中的普通文件。");
                    }
                    definitions.add(read(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), entry.getFileName().toString()));
                    if (definitions.size() > 64) { throw new IllegalArgumentException("Skill 目录最多六十四项。"); }
                }
            }
            var candidate = new SkillCatalogSnapshot(definitions); guard.run(); current = candidate; return candidate;
        } catch (IOException | RuntimeException invalid) { throw new IllegalStateException("Skill 目录加载失败，请检查本地目录、格式、重复名称和容量。"); }
    }
    /**
     * 有界读取 UTF-8 正文并解析严格的 YAML 头，不保留文件句柄或物理路径到快照。
     * @param stream 原文件流，本方法负责关闭
     * @param directoryId 由受控目录固定的名称
     * @return 完整不可变 Skill
     * @throws IOException 当文件读取或 UTF-8 解码失败时
     * @throws IllegalArgumentException 当头、字段或正文非法时
     */
    private SkillDefinition read(InputStream stream, String directoryId) throws IOException {
        final byte[] bytes;
        try (stream) { bytes = stream.readNBytes(65537); }
        if (bytes.length > 65536) { throw new IllegalArgumentException("单个 SKILL.md 不能超过 64 KiB。"); }
        var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().replace("\r\n", "\n");
        if (text.startsWith("\uFEFF")) { text = text.substring(1); }
        int end = text.indexOf("\n---\n", 4);
        if (!text.startsWith("---\n") || end < 0 || end > 8192 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("SKILL.md 必须具有有界 YAML 头和 Markdown 正文。");
        }
        Map<?, ?> header = yaml.readValue(text.substring(4, end), Map.class);
        var allowed = Set.of("name", "description", "version", "enabled", "requires-tools");
        if (header == null || header.keySet().stream().anyMatch(key -> !(key instanceof String name) || !allowed.contains(name))
                || !(header.get("name") instanceof String id) || !directoryId.equals(id)
                || !(header.get("description") instanceof String description) || !(header.get("version") instanceof Integer version)) {
            throw new IllegalArgumentException("Skill 头字段、目录名称或版本无效。");
        }
        Object enabled = header.containsKey("enabled") ? header.get("enabled") : Boolean.TRUE;
        Object requires = header.containsKey("requires-tools") ? header.get("requires-tools") : List.of();
        if (!(enabled instanceof Boolean active) || !(requires instanceof List<?> values)) { throw new IllegalArgumentException("Skill 启用或依赖类型无效。"); }
        var tools = new ArrayList<String>();
        for (var value : values) {
            if (!(value instanceof String name)) { throw new IllegalArgumentException("Skill 工具依赖必须为名称列表。"); }
            tools.add(name);
        }
        return new SkillDefinition(id, description, version, active, tools, text.substring(end + 5).strip());
    }
}
