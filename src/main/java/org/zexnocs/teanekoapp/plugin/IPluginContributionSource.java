package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import java.util.List;

/**
 * 各上层模块贡献静态安装说明的中立端口，宿主不反射或依赖业务实现类型。
 * <br>2026/11/07：只在目录初始化或显式重扫调用，不读取配置值或启动业务。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IPluginContributionSource {
    /**
     * 返回包含停用项的完整安装说明；无动态状态和秘密数据。
     * @return 由实际已注册声明投影的贡献列表
     */
    List<PluginContribution> contributions();
}
