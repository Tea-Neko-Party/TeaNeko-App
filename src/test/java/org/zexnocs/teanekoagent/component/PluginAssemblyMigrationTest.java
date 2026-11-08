package org.zexnocs.teanekoagent.component;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.automation.AutomationAssemblyMigration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证业务插件归属变化时保留原权限选择，冲突不覆盖旧值。
 * <br>2026/11/07：覆盖记忆合并、命令分离及重复迁移。
 * <br>2026/11/08：验证删除宿主业务属性后原始字段类型、关闭选择和秘密仍可无损往返。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginAssemblyMigrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 旧跨聊天开关原样合并，重复转换不改变结果。 */
    @Test void mergesCrossChatRecallWithoutChangingPermission() {
        var migration = new CognitionAssemblyMigration();
        var document = document("\"memory\":{\"memoryReadEnabled\":false},\"shared-memory\":{\"enabled\":true}");
        migration.migrate(document);
        assertTrue(document.at("/components/memory/crossChatRecallEnabled").asBoolean());
        assertFalse(document.at("/components/memory/memoryReadEnabled").asBoolean());
        assertFalse(document.path("components").has("shared-memory"));
        var once = document.deepCopy();
        migration.migrate(document);
        assertEquals(once, document);
        var absent = document("\"memory\":{}");
        migration.migrate(absent);
        assertFalse(absent.at("/components/memory").has("crossChatRecallEnabled"));
    }

    /** 新旧跨聊天开关冲突必须保留原树并报告。 */
    @Test void rejectsConflictingCrossChatSettings() {
        var document = document("\"memory\":{\"crossChatRecallEnabled\":false},\"shared-memory\":{\"enabled\":true}");
        var original = document.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> new CognitionAssemblyMigration().migrate(document));
        assertEquals(original, document);
    }

    /** 原命令开关关闭时不安装新能力，原开启时完整保留且转换幂等。 */
    @Test void separatesCommandsOnlyWhenPreviouslyAllowed() {
        var migration = new AutomationAssemblyMigration();
        var disabled = document("\"tool\":{\"commandToolsEnabled\":false,\"allowedTools\":\"read\"}");
        migration.migrate(disabled);
        assertFalse(disabled.path("components").has("command"));
        assertEquals("read", disabled.at("/components/tool/allowedTools").asString());
        var enabled = document("\"tool\":{\"commandToolsEnabled\":true}");
        migration.migrate(enabled);
        assertTrue(enabled.at("/components/command/commandToolsEnabled").asBoolean());
        var once = enabled.deepCopy();
        migration.migrate(enabled);
        assertEquals(once, enabled);
    }

    /** 新旧命令值冲突时不得静默选择任一方。 */
    @Test void rejectsConflictingCommandSettings() {
        var document = document("\"tool\":{\"commandToolsEnabled\":true},\"command\":{\"commandToolsEnabled\":false}");
        var original = document.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> new AutomationAssemblyMigration().migrate(document));
        assertEquals(original, document);
    }

    /** 2026/11/08：真实序列化保留旧业务 false、未知对象、null 及秘密，不创建额外包装节点。 */
    @Test
    void retainsUnknownFieldsBeforePluginMigration() {
        var original = JSON.readTree("""
                {"enabled":true,"memoryReadEnabled":false,"personalityLearningEnabled":false,
                 "extension":{"secret":"测试凭据","values":[false,3,null]},"unknown":null}
                """);
        var config = JSON.treeToValue(original,
                org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData.class);
        var copy = config.retainedFields();
        ((ObjectNode) copy.get("extension")).put("secret", "外部修改");
        var restored = JSON.valueToTree(config);
        assertEquals(original.path("memoryReadEnabled"), restored.path("memoryReadEnabled"));
        assertEquals(original.path("personalityLearningEnabled"), restored.path("personalityLearningEnabled"));
        assertEquals(original.path("extension"), restored.path("extension"));
        assertTrue(restored.has("unknown"));
        assertTrue(restored.path("unknown").isNull());
        assertTrue(restored.path("components").isNull());
        assertFalse(restored.has("retainedFields"));
    }

    /**
     * @param components 本例显式业务组件
     * @return 含必要模型组件的独立配置树
     */
    private static ObjectNode document(String components) {
        return (ObjectNode) JSON.readTree("{\"version\":1,\"components\":{\"llm\":{}," + components + "}}");
    }
}
