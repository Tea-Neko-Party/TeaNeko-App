package org.zexnocs.teanekoapp.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;

/**
 * 用于获取当前架构的版本号
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@Component
public class VersionUtil {
    @Value("${app.version:unknown}")
    private String version;

    public String getVersion() {
        if ("unknown".equals(version)) {
            String jarVersion = TeaNekoAppApplication.class
                    .getPackage()
                    .getImplementationVersion();
            return jarVersion != null ? jarVersion : "dev";
        }
        return version;
    }
}
