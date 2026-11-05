package org.zexnocs.teanekoagent.integration.message;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 原生图文容量和引用失败的安全诊断，不连接实际客户端。
 * <br>2026/11/06：分别核验张数、单引用、总量和非法引用，不静默裁剪图片。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
class NativeMessageInputTest {
    /** 上限以内保留图片顺序，第五张单独报告张数而不是笼统引用失败。 */
    @Test void countsImagesWithoutSilentlyDroppingThem() {
        var data = source();
        var content = new DefaultTeaNekoContentListBuilder();
        for (int index = 0; index < 4; index++) { content.addImage("https://example.invalid/image-" + index); }
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(content.build());
        assertEquals(4, NativeMessageInput.from(data).message().getContents().stream().filter(item -> "image".equals(item.getType())).count());
        content.addImage("https://example.invalid/fifth");
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(content.build());
        var failure = assertThrows(NativeMessageInputException.class, () -> NativeMessageInput.from(data));
        assertTrue(failure.failureReason().contains("图片数=5"));
        assertTrue(failure.failureReason().contains("上限=4"));
    }

    /** 单引用、总引用及文本容量分别报告数值，非法地址不会泄露参数或文件路径。 */
    @Test void reportsExactBoundaryWithoutLeakingReference() {
        var data = source();
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(new DefaultTeaNekoContentListBuilder().addImage("https://example.invalid/" + "x".repeat(2_097_152)).build());
        assertTrue(assertThrows(NativeMessageInputException.class, () -> NativeMessageInput.from(data)).failureReason().contains("单引用上限"));
        var image = "https://example.invalid/" + "x".repeat(1_100_000);
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(new DefaultTeaNekoContentListBuilder().addImage(image).addImage(image).build());
        assertTrue(assertThrows(NativeMessageInputException.class, () -> NativeMessageInput.from(data)).failureReason().contains("总字符数"));
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(new DefaultTeaNekoContentListBuilder().addText("字".repeat(65537)).build());
        assertTrue(assertThrows(NativeMessageInputException.class, () -> NativeMessageInput.from(data)).failureReason().contains("65537"));
        org.mockito.Mockito.<java.util.List<? extends org.zexnocs.teanekoapp.message.api.ITeaNekoContent>>when(data.getMessages()).thenReturn(new DefaultTeaNekoContentListBuilder().addImage("file:///private-secret.png").build());
        var invalid = assertThrows(NativeMessageInputException.class, () -> NativeMessageInput.from(data));
        assertTrue(invalid.failureReason().contains("第1张"));
        assertTrue(invalid.failureReason().contains("HTTP(S)"));
        assertFalse(FailureDiagnostic.from(invalid).toString().contains("private-secret"));
    }

    /** @return 只带上下文所需身份的可控消息，不提供网络资源 */
    private static ITeaNekoMessageData source() {
        var data = mock(ITeaNekoMessageData.class, RETURNS_DEEP_STUBS);
        when(data.getTime()).thenReturn(Instant.now());
        when(data.getUserData().getUuid()).thenReturn(UUID.randomUUID());
        return data;
    }
}
