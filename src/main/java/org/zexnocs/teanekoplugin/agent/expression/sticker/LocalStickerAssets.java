package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.DiagnosticException;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * 可信主机下载、实际解码和内容地址存储；原动画保留，预览仅用于同次视觉任务。
 * 所有阻塞操作由采集宿主放在虚拟执行域，构造期不创建目录或网络客户端。
 * <br>2026/11/03：持有发送占用时按内容地址读取原图并验证完整性。
 * <br>2026/11/03：管理预览与视觉刷新复用相同解码预算。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：保留素材下载真实原因，分别报告域名、网络、HTTP 状态和大小限制。
 * <br>2026/11/05：按真实动画子帧计解码预算，支持有界长动画并公开格式及尺寸限制原因。
 * <br>2026/11/06：I/O 边界保留安全类型和位置，不将本机路径或原始异常文本带入公开结果。
 * <br>2026/11/07：保留安全 I/O 分类与位置，并展开文件资源的拥有和释放边界。
 * <br>2026/11/07：图片和描述共用卷身份与受控路径。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Component @TeaNekoStickerAssets("local")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class LocalStickerAssets implements IStickerAssetStore {
    /**
     * @param settings 固定目录
     * @return 存储卷持久身份
     */
    @Override
    public String identity(StickerSettings settings) {
        // 2026/11/07：沿用共同卷身份，不让描述迁移建立另一套接管规则。
        return StickerStoragePaths.identity(settings);
    }
    /**
     * @param job 原作业
     * @param settings 固定预算
     * @param control 借用控制
     * @return 验证后的暂存与预览
     */
    @Override
    public Prepared prepare(Stickers.Job job, StickerSettings settings, ExecutionControl control) {
        control.checkActive();
        byte[] bytes = download(job.getSource().url(), settings, control);
        var prepared = decode(bytes, control);
        var path = stage(job, settings);
        try {
            control.checkActive();
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            control.checkActive();
            return prepared;
        } catch (IOException failure) {
            throw new DiagnosticException("无法写入素材暂存文件。", failure);
        }
    }
    /**
     * @param job 已登记文件的原作业
     * @param settings 配置
     * @param control 控制
     * @return 原文件元数据
     */
    @Override
    public Stickers.Blob publish(Stickers.Job job, StickerSettings settings, ExecutionControl control) {
        control.checkActive();
        var blob = Objects.requireNonNull(job.getBlob());
        var target = asset(blob, settings);
        var source = stage(job, settings);
        try {
            regular(source);
            if (Files.size(source) != blob.bytes() || blob.bytes() > settings.maxFileBytes() || !digest(Files.readAllBytes(source)).equals(blob.hash())) {
                throw new IllegalStateException("素材暂存内容已变化。");
            }
            if (Files.exists(target, NOFOLLOW_LINKS)) {
                if (!exists(blob, settings)) {
                    throw new IllegalStateException("素材内容地址发生冲突。");
                }
                Files.delete(source);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
            control.checkActive();
            return blob;
        } catch (IOException failure) {
            throw new DiagnosticException("无法原子发布素材文件。", failure);
        }
    }
    /**
     * @param job 原作业
     * @param settings 配置
     */
    @Override public void discard(Stickers.Job job,StickerSettings settings){if(job.getLease()!=null){deleteFile(stage(job,settings));}}
    /**
     * @param blob 无有效占用的内容
     * @param settings 配置
     */
    @Override public void delete(Stickers.Blob blob,StickerSettings settings){deleteFile(asset(blob,settings));}
    /**
     * @param blob 固定元数据
     * @param settings 配置
     * @return 文件是否存在且摘要正确
     */
    @Override public boolean exists(Stickers.Blob blob,StickerSettings settings){
        var path=asset(blob,settings);
        try{if(!Files.isRegularFile(path,NOFOLLOW_LINKS)||Files.size(path)!=blob.bytes()||blob.bytes()>8*1024*1024){return false;}regular(path);return digest(Files.readAllBytes(path)).equals(blob.hash());}
        catch(IOException failure){return false;}
    }
    /**
     * 2026/11/03：字节只存在实际发送链，不把文件路径或来源 URL 交给模型。
     * @param blob 固定元数据
     * @param settings 原预算
     * @param control 借用控制
     * @return 有界且摘要匹配的原图
     */
    @Override
    public byte[] read(Stickers.Blob blob, StickerSettings settings, ExecutionControl control) {
        control.checkActive();
        var path = asset(blob, settings);
        try {
            regular(path);
            if (blob.bytes() < 1 || blob.bytes() > settings.maxFileBytes() || Files.size(path) != blob.bytes()) {
                throw new IllegalStateException("素材文件大小或状态已变化。");
            }
            var bytes = Files.readAllBytes(path);
            control.checkActive();
            if (!digest(bytes).equals(blob.hash())) {
                throw new IllegalStateException("素材文件指纹校验失败。");
            }
            return bytes;
        } catch (IOException failure) {
            throw new DiagnosticException("素材文件读取失败。", failure);
        }
    }
    /**
     * 2026/11/03：不复制暂存，不使用可能过期的原 URL。
     * @param blob 原元数据
     * @param settings 预算
     * @param control 借用控制
     * @return 原文件对应的有界采样
     */
    @Override
    public Prepared preview(Stickers.Blob blob, StickerSettings settings, ExecutionControl control) {
        return decode(read(blob, settings, control), control);
    }
    /**
     * 逐跳验证可信域名与解析地址，禁止代理和隐式重定向；显式域名信任不能替代网络层 DNS 隔离。
     * 2026/11/05：不再用宽泛捕获抹去下载拒绝原因，公开诊断不包含完整图片地址。
     * @param text 消息端口原引用
     * @param settings 网络预算
     * @param control 当前控制
     * @return 有界原始字节
     */
    private byte[] download(String text, StickerSettings settings, ExecutionControl control) {
        if (text.startsWith("data:image/")) {
            int comma = text.indexOf(',');
            if (comma < 0 || !text.substring(0, comma).endsWith(";base64")
                    || text.length() > (long) settings.maxFileBytes() * 4 / 3 + 128) {
                throw new StickerDownloadException("内联素材超过预算或编码无效。");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(text.substring(comma + 1));
            } catch (IllegalArgumentException failure) {
                throw new StickerDownloadException("内联素材的 Base64 编码无效。", failure);
            }
            if (bytes.length > settings.maxFileBytes()) {
                throw new StickerDownloadException("素材文件超过 max-file-bytes 限制。");
            }
            return bytes;
        }
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
            /**
             * @param uri 实际目标
             * @return 不使用进程代理
             */
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(Proxy.NO_PROXY);
            }
            /**
             * @param uri 目标
             * @param address 代理地址
             * @param failure 原失败
             */
            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            }
        }).build()) {
            var uri = downloadUri(text);
            for (int hop = 0; hop <= 3; hop++) {
                control.checkActive();
                validate(uri, settings);
                var future = client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (var pending = control.onTermination(reason -> future.cancel(true))) {
                    var response = future.get(10, TimeUnit.SECONDS);
                    try (var input = response.body(); var reading = control.onTermination(reason -> {
                        try {
                            input.close();
                        } catch (IOException ignored) {
                            /* 终止释放继续处理其他监听器。 */
                        }
                    })) {
                        if (response.statusCode() >= 300 && response.statusCode() < 400) {
                            var location = response.headers().firstValue("Location").orElseThrow(() ->
                                    new StickerDownloadException("素材重定向缺少 Location 地址。"));
                            uri = uri.resolve(downloadUri(location));
                            continue;
                        }
                        if (response.statusCode() != 200) {
                            throw httpFailure(response.statusCode());
                        }
                        if (response.headers().firstValueAsLong("Content-Length").orElse(0) > settings.maxFileBytes()) {
                            throw new StickerDownloadException("素材文件超过 max-file-bytes 限制。");
                        }
                        var output = new ByteArrayOutputStream();
                        var buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            control.checkActive();
                            if (output.size() + count > settings.maxFileBytes()) {
                                throw new StickerDownloadException("素材文件超过 max-file-bytes 限制。");
                            }
                            output.write(buffer, 0, count);
                        }
                        return output.toByteArray();
                    }
                } finally {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
            }
            throw new StickerDownloadException("素材重定向超过三次限制。");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            control.checkActive();
            throw new StickerDownloadException("素材下载线程已中断。", failure);
        } catch (ExecutionException | TimeoutException | IOException | CancellationException failure) {
            // 2026/11/05：下载 Future 被终止监听器取消时，继续保留执行控制的真实停止原因。
            control.checkActive();
            throw networkFailure(failure);
        }
    }

    /**
     * 2026/11/05：将包含签名参数的 URI 解析异常映射为可公开原因。
     * @param text 原始或相对引用
     * @return 已解析引用
     * @throws StickerDownloadException 当引用语法无效时
     */
    private URI downloadUri(String text) {
        try {
            return URI.create(text);
        } catch (IllegalArgumentException failure) {
            throw new StickerDownloadException("素材地址格式无效。", failure);
        }
    }

    /**
     * 2026/11/05：HTTP 失败只记录状态与固定说明，不读取签名地址或响应正文。
     * @param status 图片服务返回的状态
     * @return 可公开的下载异常
     */
    private StickerDownloadException httpFailure(int status) {
        String reason = switch (status) {
            case 401, 403 -> "图片服务拒绝下载，来源链接可能已过期或缺少访问权限。";
            case 404, 410 -> "来源图片不存在或链接已失效。";
            case 429 -> "图片服务限制下载频率。";
            default -> status >= 500 ? "图片服务暂时不可用。" : "图片服务未返回可用文件。";
        };
        return new StickerDownloadException("素材下载 HTTP " + status + "：" + reason);
    }

    /**
     * 2026/11/05：保留网络原因链，避免把下载故障误报为模型故障或白名单拒绝。
     * @param failure 下载或 DNS 解析的异常
     * @return 不含远端地址参数的诊断异常
     */
    private StickerDownloadException networkFailure(Exception failure) {
        var types = FailureDiagnostic.from(failure).types();
        String reason;
        if (types.stream().anyMatch(type -> type.endsWith("UnknownHostException"))) {
            reason = "素材主机 DNS 解析失败，请检查网络和域名解析。";
        } else if (types.stream().anyMatch(type -> type.endsWith("TimeoutException"))) {
            reason = "素材下载连接或读取超时。";
        } else if (types.stream().anyMatch(type -> type.endsWith("SSLException")
                || type.endsWith("SSLHandshakeException"))) {
            reason = "素材 HTTPS 握手失败，请检查证书和协议。";
        } else if (types.stream().anyMatch(type -> type.endsWith("CancellationException"))) {
            reason = "素材下载任务已取消。";
        } else if (types.stream().anyMatch(type -> type.endsWith("ConnectException"))) {
            reason = "无法连接素材服务，请检查网络和地址；素材下载不使用进程代理。";
        } else {
            reason = "素材下载发生网络或读取错误。";
        }
        return new StickerDownloadException(reason, failure);
    }

    /**
     * 2026/11/05：明确区分协议、端口、主机白名单和私有地址拒绝，只公开主机名。
     * @param uri 当前跳转目标
     * @param settings 白名单
     * @throws IOException 当域名无法验证时
     */
    private void validate(URI uri, StickerSettings settings) throws IOException {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getRawUserInfo() != null
                || uri.getFragment() != null || host.isEmpty()) {
            throw new StickerDownloadException("素材地址须为不含身份信息和片段的 HTTP(S) 链接。");
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw new StickerDownloadException("素材地址端口不受支持，仅允许默认端口、80 或 443。");
        }
        if (!matches(host, settings.allowedHosts())) {
            throw new StickerDownloadException("素材主机 " + host + " 未列入 agent/stickers.yml 的 allowed-hosts。");
        }
        for (var address : InetAddress.getAllByName(host)) {
            byte[] raw = address.getAddress();
            boolean special = address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (raw.length == 16 && (raw[0] & 0xfe) == 0xfc)
                    || (raw.length == 4 && (raw[0] & 255) == 100 && (raw[1] & 255) >= 64 && (raw[1] & 255) <= 127);
            if (special && !matches(host, settings.privateHosts())) {
                throw new StickerDownloadException("素材主机 " + host
                        + " 解析到私有或特殊地址，须在 private-hosts 中明确允许。");
            }
        }
    }
    /**
     * @param host 当前主机
     * @param patterns 显式信任域
     * @return 是否准确匹配
     */
    private boolean matches(String host,List<String> patterns){return !host.isEmpty()&&patterns.stream().map(v->v.toLowerCase(Locale.ROOT)).anyMatch(v->host.equals(v)||(v.startsWith("*.")&&host.endsWith(v.substring(1))&&host.length()>v.length()-1));}
    /**
     * 有界解码原动画并合成抽样帧；不把子帧冒充完整画布。
     * 2026/11/05：总像素按真实子帧累计，帧数上限为 600；保留画布及累计像素硬限制。
     * @param bytes 原始内容
     * @param control 当前控制
     * @return 有界采样输入
     */
    private Prepared decode(byte[] bytes, ExecutionControl control) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new StickerImageException("没有可用解码器，请使用 PNG、JPEG 或 GIF；当前运行环境未必提供 WebP 解码器。");
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (format.equals("jpeg")) {
                    format = "jpg";
                }
                if (!Set.of("png", "jpg", "gif", "webp").contains(format)) {
                    throw new StickerImageException("格式不受支持，请使用 PNG、JPEG、GIF 或已安装解码器的 WebP。");
                }
                if (format.equals("png")) {
                    validatePng(bytes);
                }
                int count = reader.getNumImages(true), width = reader.getWidth(0), height = reader.getHeight(0);
                Color background = new Color(0, true);
                if (format.equals("gif")) {
                    var tree = (IIOMetadataNode) reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                    var nodes = tree.getElementsByTagName("LogicalScreenDescriptor");
                    if (nodes.getLength() > 0) {
                        var node = (IIOMetadataNode) nodes.item(0);
                        width = Integer.parseInt(node.getAttribute("logicalScreenWidth"));
                        height = Integer.parseInt(node.getAttribute("logicalScreenHeight"));
                    }
                }
                if (format.equals("gif")) {
                    background = gifBackground((IIOMetadataNode) reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"));
                }
                if (count < 1 || count > 600) {
                    throw new StickerImageException("帧数=" + count + "，允许 1–600 帧；请缩短动画后再采集。");
                }
                if (width < 1 || height < 1 || (long) width * height > 4_194_304L) {
                    throw new StickerImageException("画布=" + width + "×" + height
                        + "，允许最多 4194304 像素；请缩小图片后再采集。");
                }
                if (count > 1 && !format.equals("gif")) {
                    throw new StickerImageException("当前解码器不支持此格式的可靠动画合成，请转换为 GIF。");
                }
                // 2026/11/05：优化 GIF 的子帧通常小于画布，不再按整幅画布乘以所有帧误计解码量。
                long decodedPixels = 0;
                for (int index = 0; index < count; index++) {
                    control.checkActive();
                    int frameWidth = reader.getWidth(index), frameHeight = reader.getHeight(index);
                    if (frameWidth < 1 || frameHeight < 1 || frameWidth > width || frameHeight > height) {
                        throw new StickerImageException("第 " + index + " 帧尺寸超出已验证画布。");
                    }
                    decodedPixels += (long) frameWidth * frameHeight;
                    if (decodedPixels > 33_554_432L) {
                        throw new StickerImageException("累计子帧解码像素=" + decodedPixels
                            + "，最多 33554432；请缩小图片或减少动画帧数。");
                    }
                }
                var canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var previews = new ArrayList<String>();
                long elapsed = 0;
                var moments = new ArrayList<Long>();
                var samples = new HashSet<Integer>();
                for (int i = 0; i < Math.min(4, count); i++) {
                    samples.add(count == 1 ? 0 : i * (count - 1) / (Math.min(4, count) - 1));
                }
                for (int index = 0; index < count; index++) {
                    control.checkActive();
                    int frameWidth = reader.getWidth(index), frameHeight = reader.getHeight(index);
                    if (frameWidth < 1 || frameHeight < 1 || frameWidth > width || frameHeight > height) {
                        throw new StickerImageException("动画子帧尺寸超出画布预算。");
                    }
                    var frame = reader.read(index);
                    int x = 0, y = 0, delay = 0;
                    String disposal = "none";
                    boolean transparent = false;
                    if (format.equals("gif")) {
                        var tree = (IIOMetadataNode) reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0");
                        var node = (IIOMetadataNode) tree.getElementsByTagName("ImageDescriptor").item(0);
                        x = Integer.parseInt(node.getAttribute("imageLeftPosition"));
                        y = Integer.parseInt(node.getAttribute("imageTopPosition"));
                        var controls = tree.getElementsByTagName("GraphicControlExtension");
                        if (controls.getLength() > 0) {
                            var gc = (IIOMetadataNode) controls.item(0);
                            delay = Integer.parseInt(gc.getAttribute("delayTime")) * 10;
                            disposal = gc.getAttribute("disposalMethod");
                            transparent = Boolean.parseBoolean(gc.getAttribute("transparentColorFlag"));
                        }
                    }
                    if (x < 0 || y < 0 || x + frame.getWidth() > width || y + frame.getHeight() > height) {
                        throw new StickerImageException("动画子帧位置超出画布。");
                    }
                    if (index == 0 && !transparent) {
                        var initial = canvas.createGraphics();
                        try {
                            initial.setColor(background);
                            initial.fillRect(0, 0, width, height);
                        } finally {
                            initial.dispose();
                        }
                    }
                    // 2026/11/05：仅保存子帧覆盖区域，避免小子帧恢复时反复复制整张大画布。
                    BufferedImage previous = disposal.equals("restoreToPrevious")
                        ? copy(canvas.getSubimage(x, y, frame.getWidth(), frame.getHeight())) : null;
                    var graphics = canvas.createGraphics();
                    try {
                        graphics.drawImage(frame, x, y, null);
                    } finally {
                        graphics.dispose();
                    }
                    if (samples.contains(index)) {
                        previews.add(preview(canvas));
                        moments.add(elapsed);
                    }
                    if (disposal.equals("restoreToBackgroundColor")) {
                        var g = canvas.createGraphics();
                        try {
                            g.setComposite(AlphaComposite.Src);
                            g.setColor(transparent ? new Color(0, true) : background);
                            g.fillRect(x, y, frame.getWidth(), frame.getHeight());
                        } finally {
                            g.dispose();
                        }
                    }
                    if (previous != null) {
                        var restored = canvas.createGraphics();
                        try {
                            restored.setComposite(AlphaComposite.Src);
                            restored.drawImage(previous, x, y, null);
                        } finally {
                            restored.dispose();
                        }
                    }
                    elapsed += delay;
                }
                return new Prepared(new Stickers.Blob(digest(bytes), format, bytes.length, width, height, count), List.copyOf(previews), "原图共" + count + "帧；采样时间毫秒=" + moments + "；未采样细节不得臆测。");
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw new DiagnosticException("图片数据损坏或不完整，无法完成解码；请重新获取图片。", failure);
        }
    }
    /**
     * @param tree 原 GIF 全局元数据
     * @return 全局背景色，缺失时保留透明
     */
    private Color gifBackground(IIOMetadataNode tree){
        var tables=tree.getElementsByTagName("GlobalColorTable");if(tables.getLength()==0){return new Color(0,true);}var table=(IIOMetadataNode)tables.item(0);String index=table.getAttribute("backgroundColorIndex");
        for(int i=0;i<table.getChildNodes().getLength();i++){var color=(IIOMetadataNode)table.getChildNodes().item(i);if(index.equals(color.getAttribute("index"))){return new Color(Integer.parseInt(color.getAttribute("red")),Integer.parseInt(color.getAttribute("green")),Integer.parseInt(color.getAttribute("blue")));}}
        return new Color(0,true);
    }
    /**
     * 校验静态 PNG 块，明确拒绝当前解码器不能合成的动画。
     * 2026/11/05：APNG 的不支持原因直接报告，不与图片损坏混淆。
     * @param bytes 已确定为 PNG 的原内容
     * @throws IOException 当图片块长度无效或读取失败时
     * @throws StickerImageException 当输入为 APNG 动画时
     */
    private void validatePng(byte[] bytes)throws IOException{
        try(var input=new DataInputStream(new ByteArrayInputStream(bytes))){
            input.skipNBytes(8);while(input.available()>=12){long length=Integer.toUnsignedLong(input.readInt());int type=input.readInt();
                if(length>input.available()-4L){throw new IOException("PNG 块长度无效。");}
                if(type==0x6163544c){throw new StickerImageException("当前 PNG 解码器不支持 APNG 动画合成，请转换为 GIF。");}
                input.skipNBytes(length+4);
            }
        }
    }
    /**
     * @param source 原画布
     * @return 独立透明画布
     */
    private BufferedImage copy(BufferedImage source){var result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);var g=result.createGraphics();try{g.drawImage(source,0,0,null);}finally{g.dispose();}return result;}
    /**
     * @param image 完整画布
     * @return 缩小且编码后的 PNG
     */
    private String preview(BufferedImage image) {
        double scale = Math.min(1, 512.0 / Math.max(image.getWidth(), image.getHeight()));
        var small = new BufferedImage(Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        var g = small.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, small.getWidth(), small.getHeight());
            g.drawImage(image, 0, 0, small.getWidth(), small.getHeight(), null);
        } finally {
            g.dispose();
        }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(small, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException failure) {
            throw new DiagnosticException("素材预览编码失败。", failure);
        }
    }
    /**
     * @param settings 宿主目录
     * @return 无符号链接的实例根
     */
    private Path root(StickerSettings settings) {
        // 2026/11/07：图片与描述使用同一父路径校验。
        return StickerStoragePaths.root(settings);
    }
    /**
     * @param job 固定作业及租约
     * @param settings 配置
     * @return 唯一受控暂存键
     */
    private Path stage(Stickers.Job job,StickerSettings settings){return root(settings).resolve("staging").resolve(job.getId()+"-"+Objects.requireNonNull(job.getLease())+".part");}
    /**
     * @param blob 固定摘要
     * @param settings 配置
     * @return 受控内容地址
     */
    private Path asset(Stickers.Blob blob,StickerSettings settings){if(!blob.hash().matches("[a-f0-9]{64}")||!Set.of("png","jpg","gif","webp").contains(blob.format())){throw new IllegalArgumentException("素材存储键无效。");}return root(settings).resolve("assets").resolve(blob.hash()+"."+blob.format());}
    /**
     * @param path 精确受控文件
     * @throws IOException 当不是普通文件时
     */
    private void regular(Path path)throws IOException{if(!Files.isRegularFile(path,NOFOLLOW_LINKS)||Files.isSymbolicLink(path)){throw new IOException("素材文件类型无效。");}}
    /** @param path 精确受控文件 */
    private void deleteFile(Path path) {
        try {
            if (Files.exists(path, NOFOLLOW_LINKS)) {
                regular(path);
                Files.delete(path);
            }
        } catch (IOException failure) {
            throw new DiagnosticException("素材文件尚未成功回收。", failure);
        }
    }
    /**
     * @param bytes 原始内容
     * @return SHA-256 指纹
     */
    private String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException failure){throw new IllegalStateException("系统缺少 SHA-256。");}}
}
