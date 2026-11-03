package org.zexnocs.teanekoapp.web.settings;

import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import java.util.concurrent.CompletionStage;

/**
 * 业务模块贡献中文字段及专用只读绑定，通用 Web 不反射选择存储类型或扩权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IChatSettingsSection {
    /** @return 人可理解的业务名称 */
    String name();

    /** @return 仅由本地代码提供的配置管理声明 */
    ConfigManager manager();

    /**
     * @param target 已解析真实目标
     * @param snapshot 实际原子当前读
     * @return 业务安全字段，不导出任意对象
     */
    CompletionStage<ChatSettings.View> view(ChatTargets.Resolved target, ConfigEdits.Snapshot snapshot);

    /**
     * @param change 明确写操作
     * @throws IllegalArgumentException 当字段不属于此入口时
     */
    void validate(ConfigEdits.Change change);
}
