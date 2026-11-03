package org.zexnocs.teanekoagent.adapter.config;

import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataObject;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * 将新 Agent 的统一作用域开关接入现有 /cfg 注册与权限链，管理入口不经过 Agent 门控。
 * 该管理器只声明配置，构造 Bean 不加载作用域数据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@ConfigManager(value = "agent", description = "Agent 作用域、人格与记忆开关、活动模式和历史窗口",
        storage = TeaNekoAgentEasyDataObject.class,
        configType = AgentScopeConfigData.class, fieldChecker = AgentScopeFieldChecker.class,
        namespaces = {TeaNekoConfigNamespaces.GENERAL, TeaNekoConfigNamespaces.PRIVATE, TeaNekoConfigNamespaces.GROUP})
public class AgentScopeConfigManager {}
