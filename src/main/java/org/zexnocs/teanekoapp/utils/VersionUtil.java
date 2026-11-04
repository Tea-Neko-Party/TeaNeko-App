package org.zexnocs.teanekoapp.utils;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 用于获取当前架构的版本号
 * <br>2026/11/04：优先使用产物清单，再读取构建生成资源，普通类路径启动不再仅显示 dev。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 * @version 4.7.1-l
 */
@Getter
@Component
public class VersionUtil {
    // 2026/11/04：版本属于当前构建，不受运行配置或启动参数伪造。
    private final String version;

    /** 捕获本次构建的版本，不读取工作目录中的源码版本文件。 */
    public VersionUtil() {
        version = readVersion(TeaNekoAppApplication.class.getPackage().getImplementationVersion(),
                VersionUtil.class.getResourceAsStream("/teaneko-version.properties"));
    }

    /**
     * @param manifest 当前类型所属 JAR 的版本，无清单时为空
     * @param generated 构建生成资源，读取后由本方法关闭，未打包时为空
     * @return 真实清单或构建资源版本，完全未知时为 dev
     * @throws IllegalStateException 当已有构建资源损坏或无法读取时
     */
    static String readVersion(@Nullable String manifest, @Nullable InputStream generated) {
        try (generated) {
            if (manifest != null && !manifest.isBlank()) {
                return manifest;
            }
            if (generated == null) {
                return "dev";
            }
            var properties = new Properties();
            properties.load(generated);
            String value = properties.getProperty("version");
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("构建版本资源缺少有效版本。");
            }
            return value;
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取构建版本资源。", failure);
        }
    }
}
