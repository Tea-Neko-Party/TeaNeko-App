package org.zexnocs.teanekoagent._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.schedule.NativeScheduleAdapter;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.schedule.AgentScheduleService;
import org.zexnocs.teanekoagent.schedule.EasyDataScheduleStore;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;

/**
 * 安装普通提醒及专用存储，构造期间不启动平台动作；调度由应用就绪生命周期启动。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentScheduleConfig {
    /**
     * 复用 Agent 专用 EasyData 事务入口。
     * @param data 专用数据入口
     * @param mapper 现有存储转换器
     * @return 调度存储
     */
    @Bean public EasyDataScheduleStore agentScheduleStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        return new EasyDataScheduleStore(data, mapper);
    }

    /**
     * 只注册提醒，不给后台任务增加命令、文件或代码能力。
     * @param store 实际专用存储
     * @param adapter 当前权限及发送适配
     * @return 由 Spring 关闭的单实例调度服务
     */
    @Bean(destroyMethod = "close") public AgentScheduleService agentScheduleService(EasyDataScheduleStore store, NativeScheduleAdapter adapter) {
        return new AgentScheduleService(store, Clock.systemUTC(), adapter, Map.of("remind", adapter));
    }
}
