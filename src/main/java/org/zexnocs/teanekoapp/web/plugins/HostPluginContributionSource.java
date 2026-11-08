package org.zexnocs.teanekoapp.web.plugins;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.plugin.PluginContribution;
import org.zexnocs.teanekoapp.plugin.IPluginContributionSource;
import org.zexnocs.teanekoapp.web.files.FileSettingsDirectory;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekocore.command.CommandCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 从已有宿主注册表投影配置、命令与页面的静态贡献，不取得业务调用资格。
 * <br>2026/11/07：元数据解析只由总贡献目录的初始化或显式重扫触发。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class HostPluginContributionSource implements IPluginContributionSource {
    private final FileSettingsDirectory files;
    private final ChatSettingsDirectory chats;
    private final PluginConsoleDirectory pages;
    private final CommandCatalog commands;

    /**
     * 借用已发布的业务目录，不调用底层发现扫描器。
     * @param files 完整文件配置声明
     * @param chats 完整聊天配置声明
     * @param pages 完整页面声明
     * @param commands 原命令注册表
     */
    public HostPluginContributionSource(FileSettingsDirectory files, ChatSettingsDirectory chats,
            PluginConsoleDirectory pages, CommandCatalog commands) {
        this.files = files;
        this.chats = chats;
        this.pages = pages;
        this.commands = commands;
    }

    /** @return 不含当前配置值、历史记录和执行对象的完整安装说明 */
    @Override
    public List<PluginContribution> contributions() {
        var result = new ArrayList<PluginContribution>();
        files.entries().forEach((id, entry) -> {
            var owner = files.pluginId(id);
            if (owner != null) {
                result.add(new PluginContribution(owner, "file-config", id,
                        entry.bean().name(), entry.bean().description()));
            }
        });
        chats.entries().forEach((id, entry) -> {
            if (entry.pluginId() != null) {
                result.add(new PluginContribution(entry.pluginId(), "chat-config", id,
                        entry.section().name(), entry.section().manager().description()));
            }
        });
        for (var page : pages.installed()) {
            result.add(new PluginContribution(page.pluginId(), "page", page.key(),
                    page.page().title(), page.page().description()));
        }
        for (var command : commands.installedDescription()) {
            if (command.pluginId() != null) {
                result.add(new PluginContribution(command.pluginId(), "command", command.id(),
                        command.name(), command.description()));
            }
        }
        return List.copyOf(result);
    }
}
