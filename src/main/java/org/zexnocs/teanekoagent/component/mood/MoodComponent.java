package org.zexnocs.teanekoagent.component.mood;

import org.springframework.beans.factory.ObjectProvider;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.component.social.SocialComponent;
import org.zexnocs.teanekoagent.component.social.SocialState;
import org.zexnocs.teanekoagent.component.social.SocialStateService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 与长期关系独立的短期心情，随时间回归平静并允许被安慰修复。
 * <br>2026/11/06：情绪只调整人格表达，不阻断正常命令或形成权限。
 * <br>2026/11/06：声明阶段不创建运行服务，避免组件目录与空间策略循环依赖。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@TeaNekoAgentComponent(id = "mood", order = 46, name = "心情", description = "对本轮互动产生短期情绪，随时间恢复平静。", requires = {"memory", "tool"})
@PluginOwned("teaneko.agent")
public final class MoodComponent extends SocialComponent {
    /**
     * 2026/11/06：只保存服务提供者，实际状态处理时再取得对应单例。
     * @param states 事务状态服务提供者
     * @param access 当前配置服务提供者
     * @param references 空间复核服务提供者
     */
    public MoodComponent(ObjectProvider<SocialStateService> states, ObjectProvider<SocialAccess> access,
                         ObjectProvider<IAgentMemoryReferences> references) {
        super("mood", Map.of("enabled", true, "decayHours", 12), List.of(
                new Field("enabled", "启用心情", "善意、道歉与受伤可以改变表达。", "BOOLEAN"),
                new Field("decayHours", "恢复平静小时", "1–168；超过该时长回归平静，不永久累积负面情绪。", "NUMBER")), states, access, references);
    }
    /**
     * @param value 参数
     * @throws IllegalArgumentException 恢复时长越界时
     */
    @Override public void validate(JsonNode value) {
        super.validate(value);
        range(value, "decayHours", 12, 1, 168);
    }
    /**
     * @param state 当前关系
     * @param settings 参数
     * @return 衰减后的心情
     */
    @Override protected String describe(SocialState state, JsonNode settings) {
        int mood = state.currentMood(Instant.now(), settings.path("decayHours").asInt(12));
        return "当前心情：" + (mood < -35 ? "受伤或有些生气" : mood < -10 ? "有些失落" : mood > 35 ? "愉快而亲近" : mood > 10 ? "开心" : "平静")
                + "。表现方式由原人格决定；真诚道歉与安慰可以缓和情绪。";
    }
}
