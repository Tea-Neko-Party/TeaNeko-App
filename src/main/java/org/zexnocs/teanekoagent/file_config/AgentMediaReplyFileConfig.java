package org.zexnocs.teanekoagent.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.expression.MediaReplySettings;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.Map;

/**
 * 类别选图与原生表情本地配置，和采集开关分开。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value="media-replies",path="agent",type=FileConfigType.YAML)
public class AgentMediaReplyFileConfig implements IFileConfigData {
    private boolean enabled=true;
    private int maxPerReply=2,maxCategories=2,cooldownTurns=3,maxCategoryCharacters=7800;
    private Map<String,Map<String,String>> nativeFaces=Map.of("onebot",Map.of("14","微笑","5","流泪","32","疑问","76","赞","66","爱心"));
    /** @return 已验证并深层冻结的媒体预算 */
    public MediaReplySettings snapshot(){return new MediaReplySettings(enabled,maxPerReply,maxCategories,cooldownTurns,maxCategoryCharacters,nativeFaces);}
}
