package org.zexnocs.teanekoagent.file_config.personality;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 保留旧人格文件字段的配置 DTO，使用前由适配器冻结成基础人格，不直接写入组件。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.4.1
 * @version 4.5.0-l
 */
@Getter @Setter @NoArgsConstructor
public class AgentPersonalityDefinition {
    private String id = "";
    private String displayName = "";
    private int version = 1;
    private Identity identity = new Identity();
    private List<String> coreTraits = new ArrayList<>();
    private List<String> speakingStyle = new ArrayList<>();
    private Boundaries boundaries = new Boundaries();
    private LearningPolicy learningPolicy = new LearningPolicy();

    /**
     * 基础身份约束，只能来自可信文件或明确的自定义人格配置。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.4.1
     * @version 4.5.0-l
     */
    @Getter @Setter @NoArgsConstructor
    public static class Identity {
        private String name = "";
        private List<String> immutable = new ArrayList<>();
    }
    /**
     * 人格软硬边界，不代替实际执行权限。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.4.1
     * @version 4.5.0-l
     */
    @Getter @Setter @NoArgsConstructor
    public static class Boundaries {
        private List<String> hard = new ArrayList<>();
        private List<String> soft = new ArrayList<>();
    }
    /**
     * 原有允许/禁止字段，解析后仍受代码绝对白名单限制。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.4.1
     * @version 4.5.0-l
     */
    @Getter @Setter @NoArgsConstructor
    public static class LearningPolicy {
        private List<String> mutableFields = new ArrayList<>();
        private List<String> immutableFields = new ArrayList<>();
    }
}
