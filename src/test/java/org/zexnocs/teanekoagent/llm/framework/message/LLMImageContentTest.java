package org.zexnocs.teanekoagent.llm.framework.message;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证统一图文内容的顺序、不可变表示与安全引用校验。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMImageContentTest {
    /**
     * 验证混合图文保持顺序，已构建列表不受后续构建器修改影响。
     */
    @Test
    void builderKeepsMixedContentOrderAndProducesIndependentImmutableLists() {
        var builder = LLMContentListBuilder.builder().addText("before").addImage("https://example.test/a.png", LLMImageDetail.LOW)
                .addText("after");
        var contents = builder.build();
        builder.addImage("https://example.test/b.png");
        assertEquals(List.of("text", "image", "text"), contents.stream().map(ILLMContent::getType).toList());
        assertEquals(LLMImageDetail.LOW, ((ImageLLMContentPart) contents.get(1).getContentPart()).getDetail());
        assertEquals("after", ((TextLLMContentPart) contents.get(2).getContentPart()).getText());
        assertThrows(UnsupportedOperationException.class, contents::clear);
    }

    /**
     * 验证内联图片复制字节数据，不保存调用方可变数组。
     */
    @Test
    void inlineBytesAreCapturedWithoutKeepingMutableInput() {
        var bytes = new byte[]{1, 2, 3};
        var image = ImageLLMContentPart.fromBytes("image/png", bytes, LLMImageDetail.AUTO);
        bytes[0] = 99;
        assertEquals("data:image/png;base64,AQID", image.getUrl());
        var contents = LLMContentListBuilder.builder().addImageBytes("image/png", new byte[]{1, 2, 3}).build();
        assertEquals(image.getUrl(), ((ImageLLMContentPart) contents.getFirst().getContentPart()).getUrl());
    }

    /**
     * 验证图片引用验证不读取本地文件或发起远程下载。
     */
    @Test
    void validatesReferencesWithoutReadingFilesOrFetchingUrls() {
        for (var source : List.of("file:///private.png", "javascript:alert(1)", "local.png", "https://user:secret@example.test/a",
                "https://example.test/image#fragment", "data:image/svg+xml;base64,AQID", "data:image/png;base64,",
                "data:image/png;base64,!!!!", "data:image/png;base64,A", "https://")) {
            var error = assertThrows(IllegalArgumentException.class, () -> new ImageLLMContentPart(source));
            assertFalse(error.toString().contains(source));
            assertNull(error.getCause());
        }
        assertThrows(IllegalArgumentException.class, () -> new ImageLLMContentPart(null));
        assertThrows(IllegalArgumentException.class, () -> ImageLLMContentPart.fromBytes(null, new byte[]{1}, LLMImageDetail.AUTO));
        assertThrows(IllegalArgumentException.class, () -> ImageLLMContentPart.fromBytes("image/png", new byte[0], LLMImageDetail.AUTO));
        assertEquals("https://example.test/image?signature=test", new ImageLLMContentPart("https://example.test/image?signature=test").getUrl());
    }

    /**
     * 验证通用消息 JSON 保留图片结构而非拼接为字符串。
     */
    @Test
    void genericMessageJsonKeepsImageDataAsStructuredContent() {
        var message = LLMUserMessage.builder().contents(LLMContentListBuilder.builder()
                .addText("describe").addImage("https://example.test/a.png", LLMImageDetail.HIGH).build()).build();
        var json = new ObjectMapper().writeValueAsString(message);
        assertTrue(json.contains("\"content\":["));
        assertTrue(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"url\":\"https://example.test/a.png\""));
        assertTrue(json.contains("\"detail\":\"high\""));
        assertFalse(json.contains("ImageLLMContentPart@"));
    }

    /**
     * 验证纯文本序列化兼容既有格式且不导出透明供应商状态。
     */
    @Test
    void textOnlyJsonStillMergesTextAndDoesNotExportProviderState() {
        var message = LLMAssistantMessage.builder().contents(LLMContentListBuilder.builder().addText("one").addText("two").build())
                .providerMetadata(java.util.Map.of("privateState", "opaque-test-state")).build();
        var json = new ObjectMapper().writeValueAsString(message);
        assertTrue(json.contains("\"content\":\"one two\""));
        assertFalse(json.contains("opaque-test-state"));
    }
}
