package org.zexnocs.teanekoagent.file_config.personality;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文件形式保存的 Agent 默认人格配置。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@FileConfig(
        value = "personality",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class AgentPersonalityFileConfig implements IFileConfigData {
    /**
     * 默认 agent ID。
     */
    private String defaultAgentId = "teaneko";

    /**
     * 文件中声明的角色人格列表。
     */
    private List<AgentPersonalityDefinition> characters = new ArrayList<>();

    /**
     * 按 agent ID 查找人格定义。
     *
     * @param agentId agent ID，空值时使用默认 agent ID。
     * @return 人格定义。
     */
    public Optional<AgentPersonalityDefinition> findPersonality(String agentId) {
        var id = agentId == null || agentId.isBlank() ? defaultAgentId : agentId.trim();
        if (characters == null) {
            return Optional.empty();
        }
        return characters.stream()
                .filter(character -> character.getId() != null && character.getId().equals(id))
                .findFirst();
    }
}
