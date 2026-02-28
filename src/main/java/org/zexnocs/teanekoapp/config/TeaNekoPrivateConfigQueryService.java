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
 * 私聊相关配置查询服务。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Service
public class TeaNekoPrivateConfigQueryService extends AbstractConfigDataQueryService<CommandData<?>> {
    public static final String NAMESPACE = "private_config";

    @Autowired
    public TeaNekoPrivateConfigQueryService(ConfigDataGetService configDataGetService,
                                            ConfigManagerScanner configManagerScanner,
                                            ObjectMapper objectMapper,
                                            ILogger logger) {
        super(configDataGetService, configManagerScanner, objectMapper, logger);
    }

    /**
     * 获取该 query 所属的命名空间。
     *
     * @return {@link String[] }
     */
    @Override
    public String[] getNamespaces() {
        return new String[]{
                NAMESPACE
        };
    }

    /**
     * 根据 T 获取 key。
     *
     * @param commandData 查询所需的数据
      * @return {@link IConfigKey }
      * @throws IllegalArgumentException 如果 command data 的 scope 不是 private
     */
    @Override
    public IConfigKey getConfigKey(CommandData<?> commandData) {
        // 必须是 private 类型
        if(!commandData.getScope().equals(CommandScope.PRIVATE)) {
            throw new IllegalArgumentException("只有私聊消息才支持查询私聊相关配置，实际消息类型：" + commandData.getScope());
        }
        return commandData::getScopeId;
    }
}
