package org.zexnocs.teanekoagent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.adapter.skill.LocalSkillCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证真实本地加载、严格头解析和失败发布的原子性，不建立额外文件执行框架。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LocalSkillCatalogTest {
    /**
     * 使用真实文件覆盖内容换代、失败保留、发布前授权撤销和内置示例。
     * @param root 由 JUnit 管理的临时根目录
     * @throws Exception 当测试文件无法准备时
     */
    @Test void reloadPublishesOnlyCompleteAuthorizedSnapshots(@TempDir Path root) throws Exception {
        var path = Files.createDirectory(root.resolve("sample")).resolve("SKILL.md");
        var prefix = "---\nname: sample\ndescription: 示例流程\nversion: 1\nrequires-tools: []\n---\n";
        Files.writeString(path, prefix + "原始流程");
        var environment = new MockEnvironment().withProperty("tea-neko.agent.skills.directory", root.toString())
                .withProperty("tea-neko.agent.skills.builtin-example", "false");
        var catalog = new LocalSkillCatalog(environment); var original = catalog.snapshot();
        Files.writeString(path, prefix + "更新流程"); var checks = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> catalog.reload(() -> {
            if (checks.incrementAndGet() == 2) { throw new IllegalStateException("发布前管理授权已撤销。"); }
        }));
        assertSame(original, catalog.snapshot()); var updated = catalog.reload();
        assertNotEquals(original.fingerprint(), updated.fingerprint()); assertEquals("原始流程", original.skills().get("sample").instructions());
        Files.writeString(path, prefix.replace("version: 1", "version: 1\nversion: 2") + "重复字段");
        assertThrows(IllegalStateException.class, catalog::reload); assertSame(updated, catalog.snapshot());
        Files.writeString(path, prefix.replace("name: sample", "name: ../outside") + "不能读取其他目录");
        assertThrows(IllegalStateException.class, catalog::reload); assertSame(updated, catalog.snapshot());
        Files.writeString(path, prefix + "正文".repeat(2000)); assertThrows(IllegalStateException.class, catalog::reload);
        var builtin = new LocalSkillCatalog(new MockEnvironment().withProperty("tea-neko.agent.skills.directory", root.resolve("empty").toString()));
        assertTrue(builtin.snapshot().skills().containsKey("clear-explanation"));
    }
}
