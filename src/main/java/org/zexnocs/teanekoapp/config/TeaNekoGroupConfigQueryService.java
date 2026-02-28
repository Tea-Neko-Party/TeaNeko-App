package org.zexnocs.teanekoapp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.database.configdata.AbstractConfigDataQueryService;
import org.zexnocs.teanekocore.database.configdata.ConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

/**
 * 群组相关配置查询服务。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Service
public class TeaNekoGroupConfigQueryService extends AbstractConfigDataQueryService<CommandData<?>> {
    /// 配置数据的命名空间
    public static final String NAMESPACE = "group_config";

    @Autowired
    public TeaNekoGroupConfigQueryService(ConfigDataGetService configDataGetService,
                                          ConfigManagerScanner configManagerScanner,
                                          ILogger logger) {
        super(configDataGetService, configManagerScanner, new ObjectMapper(), logger);
    }

    /**
     * 获取该 query 所属的命名空间。
     */
    @Override
    public String[] getNamespaces() {
        return new String[]{
                NAMESPACE
        };
    }

    /**
     * 根据 command data 获取配置数据的 key
     *
     * @param commandData 查询所需的数据
     * @return {@link IConfigKey }
     * @throws IllegalArgumentException 如果 command data 的 scope 不是 group
     */
    @Override
    public IConfigKey getConfigKey(CommandData<?> commandData) {
        // 必须是 group 类型
        if(!commandData.getScope().equals(CommandScope.GROUP)) {
            throw new IllegalArgumentException("只有群组消息才支持查询群组相关配置，实际消息类型：" + commandData.getScope());
        }
        return commandData::getScopeId;
    }
}
