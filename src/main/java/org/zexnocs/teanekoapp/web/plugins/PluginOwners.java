package org.zexnocs.teanekoapp.web.plugins;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 仅在目录创建时解析扩展归属，运行查询使用各业务目录保存的稳定插件 ID。
 * <br>2026/11/07：专用适配器与原配置类型的归属必须一致。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class PluginOwners {
    /** 工具类不允许创建实例。 */
    private PluginOwners() {}

    /**
     * @param types 贡献类型及其原配置类型
     * @return 声明的插件 ID，宿主未声明归属时为空
     * @throws IllegalArgumentException 当同一贡献声明不同插件归属时
     */
    public static @Nullable String resolve(Class<?>... types) {
        String owner = null;
        for (var type : types) {
            var declaration = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
            if (declaration == null) {
                continue;
            }
            if (owner != null && !owner.equals(declaration.value())) {
                throw new IllegalArgumentException("配置适配器与配置类型的插件归属冲突。");
            }
            owner = declaration.value();
        }
        return owner;
    }
}
