package org.zexnocs.teanekoagent.component.skill;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 将允许加载的技能作为独立组件参数，不把技能说明当作额外工具授权。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "skill", name = "技能", description = "按需加载显式选择的本地技能说明。", requires = "tool")
@PluginOwned("teaneko.agent")
public final class SkillComponent extends BuiltinAgentComponent {
    /** 创建空技能选择，安装本身不会执行任何技能。 */
    public SkillComponent() {
        super("skill", Map.of("skillsEnabled", true, "skills", ""), List.of(
                new Field("skillsEnabled", "允许加载", "关闭后不读取技能资料。", "BOOLEAN"),
                new Field("skills", "技能名称", "英文逗号分隔，最多八项。", "TEXT")));
    }
}
