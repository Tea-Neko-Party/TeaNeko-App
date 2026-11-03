package org.zexnocs.teanekoclient.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 有界解码并重编码静态 PNG，拒绝任意 URL、本地路径和高像素图片。
 * GIF 只取首帧；不保留原元数据、文件名或动画，不申请系统临时磁盘缓存。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class WebImages {
    public static final int MAX_BYTES = 1_048_576;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(2);

    /** @param executor 借用原业务执行域，不另建线程池 */
    public WebImages(@Qualifier("virtualExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * @param bytes 至多一 MiB 的原编码
     * @param guard 当前宿主授权
     * @return 真实解码重编码后的 PNG，不信任上传 Content-Type
     */
    public CompletionStage<byte[]> normalize(byte[] bytes, Runnable guard) {
        guard.run();
        if (bytes.length == 0 || bytes.length > MAX_BYTES || !slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("图片为空、过大或解码槽已满。"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    guard.run();
                    return decode(bytes);
                } finally {
                    slots.release();
                }
            }, executor);
        } catch (RuntimeException failure) {
            slots.release();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param bytes 原编码
     * @return 受限标准图片
     * @throws IllegalArgumentException 当格式、像素、编码或重编码容量无效时
     */
    private byte[] decode(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("图片没有可用解码器。");
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("网页只支持 PNG、JPEG 或 GIF 静态首帧。");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 4_194_304) {
                    throw new IllegalArgumentException("网页图片解码像素超过上限。");
                }
                var image = reader.read(0);
                try (var output = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", output);
                    if (output.size() == 0 || output.size() > MAX_BYTES) {
                        throw new IllegalArgumentException("标准 PNG 图片超过一 MiB 上限。");
                    }
                    return output.toByteArray();
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("网页图片无法安全解码。");
        }
    }
}
