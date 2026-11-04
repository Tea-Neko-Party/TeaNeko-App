package org.zexnocs.teanekoapp.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证版本只来自构建清单或生成资源，并检查真实类路径构建结果。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
class VersionUtilTest {
    /**
     * 普通类路径使用构建生成的版本；清单优先，坏资源不能伪装为已知版本。
     * @throws Exception 当构建资源无法读取时
     */
    @Test
    void readsGeneratedClasspathVersionAndHonorsArtifactManifest() throws Exception {
        var properties = new Properties();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/teaneko-version.properties"))) {
            properties.load(input);
        }
        assertEquals(properties.getProperty("version"), new VersionUtil().getVersion());
        assertNotEquals("dev", new VersionUtil().getVersion());
        assertEquals("jar-version", VersionUtil.readVersion("jar-version", null));
        assertEquals("dev", VersionUtil.readVersion(null, null));
        assertThrows(IllegalStateException.class, () -> VersionUtil.readVersion(null,
                new ByteArrayInputStream("other=value".getBytes(StandardCharsets.UTF_8))));
    }
}
