package org.zexnocs.teanekoapp.web.files;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import org.zexnocs.teanekocore.framework.config.ValueEditor;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 注解编辑器经过真实文件字段投影，敏感字段不进入通用编辑器。
 * <br>2026/11/06：覆盖集合本身、集合元素与类型注解。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
class ValueEditorProjectionTest {
    /** 集合字段无需伪造数字索引，字段选择优先于值类型，秘密仍被隐藏。 */
    @Test
    void exposesEditorMetadataThroughRealFileProjection() {
        var mapper = new ObjectMapper();
        var schema = new ConfigSchema(mapper, Settings.class);
        assertEquals("JSON", schema.findPointer("/items").orElseThrow().editor());
        assertEquals("LONG_TEXT", schema.findPointer("/items/0/text").orElseThrow().editor());
        assertEquals("JSON", schema.require("object").editor());
        var adapter = mock(IFileSettingsAdapter.class);
        when(adapter.fields()).thenReturn(List.of(
                new FileSettings.Rule("/items", "内容", "测试", "JSON", false),
                new FileSettings.Rule("/secret", "密钥", "测试", "TEXT", true)));
        var fields = FileSettingsPolicy.fields(mapper.valueToTree(new Settings()), adapter, true, schema);
        assertEquals("JSON", fields.getFirst().editor());
        assertEquals("AUTO", fields.getLast().editor());
        assertNull(fields.getLast().value());
    }

    /**
     * 隔离配置夹具。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    public static class Settings implements IFileConfigData {
        @ValueEditor("JSON") public List<Text> items = List.of(new Text());
        public Text object = new Text();
        @ValueEditor("LONG_TEXT") public String secret = "不应回显";
        /** 创建独立测试值。 */
        public Settings() {}
    }

    /**
     * 类型声明可复用于任意字段。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    @ValueEditor("JSON")
    public static class Text {
        @ValueEditor("LONG_TEXT") public String text = "长文本";
        /** 创建独立测试值。 */
        public Text() {}
    }
}
