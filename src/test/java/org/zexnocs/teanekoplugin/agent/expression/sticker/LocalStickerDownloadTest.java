package org.zexnocs.teanekoplugin.agent.expression.sticker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 离线验证平台图片下载、逐跳信任限制和不泄露签名的失败诊断。
 * <br>2026/11/06：核对声明大小和实际流超限时均拒绝，并释放响应流。
 * <br>2026/11/10：验证超限策略分类、数值对照、内联解码边界及安全日志。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.1-l
 */
@Timeout(15)
class LocalStickerDownloadTest {
    @TempDir
    Path directory;

    /**
     * 默认配置可下载 NT 图片，重定向与 DNS 私有地址仍不可绕过信任校验。
     * 2026/11/05：实际表情素材主机同样受准确主机与逐跳验证约束。
     * @throws Exception 当本地图片编码失败时
     */
    @Test
    void downloadsNtImagesAndKeepsTrustBoundaries() throws Exception {
        var config = new AgentStickerFileConfig();
        config.setStorageRoot(directory.toString());
        var settings = config.snapshot();
        var publicAddress = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        var privateAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        HttpClient client = mock();
        HttpClient.Builder builder = mock(RETURNS_SELF);
        HttpResponse<InputStream> response = mock();
        when(builder.build()).thenReturn(client);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(response.body()).thenAnswer(call -> new ByteArrayInputStream(bytes.toByteArray()));
        when(client.<InputStream>sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(response));

        try (var http = mockStatic(HttpClient.class); var dns = mockStatic(InetAddress.class);
             var control = ExecutionControl.create()) {
            http.when(HttpClient::newBuilder).thenReturn(builder);
            dns.when(() -> InetAddress.getAllByName("multimedia.nt.qq.com.cn"))
                    .thenReturn(new InetAddress[]{publicAddress});
            var assets = new LocalStickerAssets();
            String url = "https://multimedia.nt.qq.com.cn/download?appid=1&rkey=private-signature";
            var prepared = assets.prepare(job(url), settings, control);
            assertEquals("png", prepared.blob().format());
            dns.when(() -> InetAddress.getAllByName("gxh.vip.qq.com"))
                    .thenReturn(new InetAddress[]{publicAddress});
            assertEquals("png", assets.prepare(job("https://gxh.vip.qq.com/emoticon?rkey=private-signature"),
                    settings, control).blob().format());

            when(response.statusCode()).thenReturn(302);
            when(response.headers()).thenReturn(HttpHeaders.of(
                    Map.of("Location", List.of("https://untrusted.invalid/image?rkey=private-signature")),
                    (name, value) -> true));
            var rejected = assertThrows(IllegalArgumentException.class,
                    () -> assets.prepare(job(url), settings, control));
            assertTrue(FailureDiagnostic.from(rejected).description().contains("allowed-hosts"));
            assertFalse(FailureDiagnostic.from(rejected).toString().contains("private-signature"));

            dns.when(() -> InetAddress.getAllByName("multimedia.nt.qq.com.cn"))
                    .thenReturn(new InetAddress[]{privateAddress});
            var privateRejected = assertThrows(IllegalArgumentException.class,
                    () -> assets.prepare(job(url), settings, control));
            assertTrue(FailureDiagnostic.from(privateRejected).description().contains("private-hosts"));
            verify(client, times(3)).sendAsync(any(HttpRequest.class), any());
        }
    }

    /**
     * 2026/11/06：图片超限是正常预算拒绝，不能因没有 Content-Length 绕过，也不泄露签名。
     * @throws Exception 当响应流关闭验证失败时
     */
    @Test
    void oversizedFilesAreRejectedByHeaderAndActualBytes() throws Exception {
        var config = new AgentStickerFileConfig();
        config.setStorageRoot(directory.toString());
        config.setMaxFileBytes(1024);
        config.setPrivateHosts(List.of("multimedia.nt.qq.com.cn"));
        var address = InetAddress.getLoopbackAddress();
        HttpClient client = mock();
        HttpClient.Builder builder = mock(RETURNS_SELF);
        HttpResponse<InputStream> response = mock();
        when(builder.build()).thenReturn(client);
        when(response.statusCode()).thenReturn(200);
        when(client.<InputStream>sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(response));
        try (var http = mockStatic(HttpClient.class); var dns = mockStatic(InetAddress.class);
             var control = ExecutionControl.create()) {
            http.when(HttpClient::newBuilder).thenReturn(builder);
            dns.when(() -> InetAddress.getAllByName("multimedia.nt.qq.com.cn")).thenReturn(new InetAddress[]{address});
            var assets = new LocalStickerAssets();
            for (boolean declared : List.of(true, false)) {
                var body = spy(new ByteArrayInputStream(new byte[1025]));
                when(response.body()).thenReturn(body);
                when(response.headers()).thenReturn(HttpHeaders.of(declared
                        ? Map.of("Content-Length", List.of("1025")) : Map.of(), (name, value) -> true));
                // 2026/11/10：正常大小拒绝携带声明值或实际读取下界，资源仍实际关闭。
                var failure = assertThrows(StickerSizeLimitException.class, () -> assets.prepare(
                        job("https://multimedia.nt.qq.com.cn/download?rkey=private-signature"), config.snapshot(), control));
                var description = FailureDiagnostic.from(failure).description();
                assertTrue(description.contains(declared ? "响应声明字节数=1025" : "已读取字节数（至少）=1025"));
                assertTrue(description.contains("允许上限=1024"));
                assertTrue(description.contains("max-file-bytes=1024"));
                assertFalse(FailureDiagnostic.from(failure).toString().contains("private-signature"));
                verify(body).close();
            }
        }
    }

    /** 2026/11/10：编码长度预检和填充边界解码超限使用同一拒绝类型，不混淆非法编码。 */
    @Test
    void inlineSizeChecksDistinguishDecodedBytesFromEncodedCharacters() {
        var config = new AgentStickerFileConfig();
        config.setStorageRoot(directory.toString());
        config.setMaxFileBytes(1024);
        var assets = new LocalStickerAssets();
        try (var control = ExecutionControl.create()) {
            for (int size : List.of(1025, 1027)) {
                var inline = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[size]);
                var failure = assertThrows(StickerSizeLimitException.class,
                        () -> assets.prepare(job(inline), config.snapshot(), control));
                var description = FailureDiagnostic.from(failure).description();
                assertTrue(description.contains(size == 1025 ? "解码字节数=1025" : "Base64 字符数=1372"));
                assertTrue(description.contains("max-file-bytes=1024"));
                assertFalse(description.contains("data:image"));
            }
            var invalid = assertThrows(StickerDownloadException.class,
                    () -> assets.prepare(job("data:image/png;base64,%%%"), config.snapshot(), control));
            assertTrue(FailureDiagnostic.from(invalid).description().contains("编码无效"));
        }
    }

    /** HTTP 与 DNS 故障保留安全原因，取消下载仍以执行控制的停止状态结束。 */
    @Test
    void reportsHttpAndNetworkFailuresWithoutSignedUrls() {
        var privateAddress = InetAddress.getLoopbackAddress();
        var config = new AgentStickerFileConfig();
        config.setPrivateHosts(List.of("multimedia.nt.qq.com.cn"));
        var settings = config.snapshot();
        HttpClient client = mock();
        HttpClient.Builder builder = mock(RETURNS_SELF);
        HttpResponse<InputStream> response = mock();
        when(builder.build()).thenReturn(client);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenAnswer(call -> new ByteArrayInputStream(new byte[0]));
        when(client.<InputStream>sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(response));
        try (var http = mockStatic(HttpClient.class); var dns = mockStatic(InetAddress.class);
             var control = ExecutionControl.create()) {
            http.when(HttpClient::newBuilder).thenReturn(builder);
            dns.when(() -> InetAddress.getAllByName("multimedia.nt.qq.com.cn"))
                    .thenReturn(new InetAddress[]{privateAddress});
            var assets = new LocalStickerAssets();
            String url = "https://multimedia.nt.qq.com.cn/download?rkey=private-signature";
            var denied = assertThrows(IllegalArgumentException.class,
                    () -> assets.prepare(job(url), settings, control));
            assertTrue(FailureDiagnostic.from(denied).description().contains("HTTP 403"));
            assertFalse(FailureDiagnostic.from(denied).toString().contains("private-signature"));

            when(client.<InputStream>sendAsync(any(), any())).thenReturn(CompletableFuture.failedFuture(
                    new UnknownHostException(url)));
            var unresolved = assertThrows(IllegalArgumentException.class,
                    () -> assets.prepare(job(url), settings, control));
            var diagnostic = FailureDiagnostic.from(unresolved);
            assertTrue(diagnostic.description().contains("DNS"));
            assertTrue(diagnostic.types().contains(UnknownHostException.class.getName()));
            assertFalse(diagnostic.toString().contains("private-signature"));

            when(client.<InputStream>sendAsync(any(), any())).thenAnswer(call -> {
                control.cancel();
                return new CompletableFuture<HttpResponse<InputStream>>();
            });
            var stopped = assertThrows(ExecutionStoppedException.class,
                    () -> assets.prepare(job(url), settings, control));
            assertEquals(ExecutionControl.StopReason.CANCELLED, stopped.reason());
        }
    }

    /**
     * @param url 待验证的素材引用
     * @return 具有独立租约的下载作业
     */
    private Stickers.Job job(String url) {
        var user = UUID.randomUUID();
        var job = new Stickers.Job();
        job.setLease(UUID.randomUUID());
        job.setSource(new Stickers.Source(new AgentScope("test", "群"), user, "用户", "机器人", "消息",
                0, Instant.now(), false, user, url));
        return job;
    }
}
