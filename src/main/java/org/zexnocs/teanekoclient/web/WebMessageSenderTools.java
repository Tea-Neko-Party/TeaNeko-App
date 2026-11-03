package org.zexnocs.teanekoclient.web;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.MessageSendStages;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 网页消息通过原发送器契约进入同一持久窗口，提交后才给出真实消息号。
 * 所有发送保留原来源能力与成员版本；不进行网络抓取或未知结果重发。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class WebMessageSenderTools implements IMessageSenderTools {
    private final WebChatStore store;
    private final WebImages images;
    private final ILogger logger;
    private final Executor executor;

    /**
     * @param store 原网页持久化
     * @param images 原图片限制
     * @param logger 原任务诊断
     * @param executor 借用原执行域
     */
    public WebMessageSenderTools(WebChatStore store, WebImages images, ILogger logger,
                                 @Qualifier("virtualExecutor") Executor executor) {
        this.store = store;
        this.images = images;
        this.logger = logger;
        this.executor = executor;
    }

    /** @return 独立普通片段构建器 */
    @Override
    public ITeaNekoContentListBuilder getMsgListBuilder() {
        return DefaultTeaNekoContentListBuilder.builder();
    }

    /**
     * @param token 原调用用途，不作为认证信息
     * @param data 原网页入站消息
     * @return 当前来源的一次性发送器
     */
    @Override
    public IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data) {
        return new Easy(source(data));
    }

    /**
     * @param token 原用途
     * @param data 原网页来源
     * @return 保留节点显示名称的有界转发构造器
     */
    @Override
    public IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data) {
        return new Forward(source(data));
    }

    /**
     * @param data 原消息
     * @return 未过期的真实网页来源，不接收其他平台对象
     */
    private WebMessageData source(ITeaNekoMessageData data) {
        if (!(data instanceof WebMessageData source)) {
            throw new SecurityException("网页发送器只能借用真实网页入站来源。");
        }
        source.checkCommandSource();
        return source;
    }

    /**
     * @param source 原来源
     * @param contents 立即冻结的有界片段
     * @return 原图片保存及片段归一化阶段
     */
    private CompletionStage<List<WebChat.Part>> normalize(WebMessageData source, List<WebChat.Part> contents) {
        var result = new ArrayList<WebChat.Part>();
        CompletionStage<Void> stage = CompletableFuture.completedStage(null);
        for (var part : contents) {
            stage = stage.thenCompose(ignored -> {
                source.checkCommandSource();
                if (!part.kind().equals("INLINE_IMAGE")) {
                    result.add(part);
                    return CompletableFuture.completedStage(null);
                }
                String value = part.value();
                String encoded;
                if (value.startsWith("base64://")) {
                    encoded = value.substring(9);
                } else if (value.startsWith("data:image/") && value.indexOf(";base64,") > 0) {
                    encoded = value.substring(value.indexOf(";base64,") + 8);
                } else {
                    throw new IllegalArgumentException("网页发送不读取任意网络地址或本地图片路径。");
                }
                if (encoded.length() > 1_398_104) {
                    throw new IllegalArgumentException("网页发送图片超过一 MiB 上限。");
                }
                return images.normalize(Base64.getDecoder().decode(encoded), source::checkCommandSource)
                        .thenCompose(bytes -> store.upload(source.roomId(), UUID.randomUUID(), bytes, source::checkCommandSource))
                        .thenAccept(id -> result.add(new WebChat.Part("IMAGE", id)));
            });
        }
        return stage.thenApply(ignored -> List.copyOf(result));
    }

    /**
     * @param contents 原平台片段
     * @return 立即冻结的标量，不跨线程保存可变构建器
     */
    private List<WebChat.Part> freeze(List<ITeaNekoContent> contents) {
        if (contents.isEmpty() || contents.size() > 100) {
            throw new IllegalArgumentException("网页消息片段数量无效。");
        }
        var result = new ArrayList<WebChat.Part>();
        int characters = 0;
        int pictures = 0;
        for (var item : contents) {
            var part = item.getContentPart();
            WebChat.Part frozen;
            if (part instanceof ITextTeaNekoContentPart text) {
                frozen = new WebChat.Part("TEXT", text.getText());
            } else if (part instanceof IAtTeaNekoContentPart at) {
                frozen = new WebChat.Part("AT", at.getId());
            } else if (part instanceof IReplyTeaNekoContentPart reply) {
                frozen = new WebChat.Part("REPLY", reply.getId());
            } else if (part instanceof IImageTeaNekoContentPart image && ++pictures <= 1) {
                frozen = new WebChat.Part("INLINE_IMAGE", image.getUrl());
            } else {
                throw new IllegalArgumentException("网页消息只支持文本、提及、引用及一张受限图片。");
            }
            characters = Math.addExact(characters, frozen.value().length());
            if (characters > 1_500_000) {
                throw new IllegalArgumentException("网页消息编码总量超过上限。");
            }
            result.add(frozen);
        }
        return List.copyOf(result);
    }

    /**
     * 一次性普通消息构造器，不自动重试未知结果。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private final class Easy implements IEasyMessageSenderBuilder {
        private final WebMessageData source;
        private final ITeaNekoContentListBuilder content = DefaultTeaNekoContentListBuilder.builder();
        private Duration delay = Duration.ZERO;
        private @Nullable TaskFuture<IMessageSendResponseData> sent;

        /** @param source 固定来源 */
        Easy(WebMessageData source) {
            this.source = source;
        }

        /** @return 当前线程拥有的构建器 */
        @Override
        public ITeaNekoContentListBuilder getMessageListBuilder() {
            return content;
        }

        /** @return 原真实来源 */
        @Override
        public WebMessageData getRepliedData() {
            return source;
        }

        /** @return 同一构建器只提交一次，等待消息实际落库后返回标识 */
        @Override
        public synchronized TaskFuture<IMessageSendResponseData> sendWithFuture() {
            if (sent != null) {
                return sent;
            }
            var frozen = freeze(content.build());
            CompletionStage<Void> waiting = delay.isZero() ? CompletableFuture.completedStage(null)
                    : CompletableFuture.runAsync(source::checkCommandSource,
                            CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, executor));
            CompletionStage<IMessageSendResponseData> work = waiting.thenCompose(ignored -> normalize(source, frozen))
                    .thenCompose(parts -> store.send(source, parts)).thenApply(message -> new Response(message.id()));
            sent = TaskFuture.fromStage(logger, "网页消息持久发送", work);
            return sent;
        }

        /**
         * @param delay 原发送延迟，最多三十秒
         * @return 当前构造器
         */
        @Override
        public IEasyMessageSenderBuilder setDelay(Duration delay) {
            if (delay.isNegative() || delay.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("网页发送延迟须在零至三十秒之间。");
            }
            this.delay = delay;
            return this;
        }

        /**
         * @param count 网页发送仅接受零次重试
         * @return 当前构造器
         */
        @Override
        public IEasyMessageSenderBuilder setRetryCount(int count) {
            if (count != 0) {
                throw new IllegalArgumentException("网页持久发送不允许自动重放。");
            }
            return this;
        }

        /**
         * @param interval 无重试时仍检查参数非负
         * @return 当前构造器
         */
        @Override
        public IEasyMessageSenderBuilder setRetryInterval(Duration interval) {
            if (interval.isNegative()) {
                throw new IllegalArgumentException("重试间隔不能为负数。");
            }
            return this;
        }

        /**
         * @param recordFailed 不创建失败自动补发队列，原任务仍返回异常
         * @return 当前构造器
         */
        @Override
        public IEasyMessageSenderBuilder setRecordFailed(boolean recordFailed) {
            return this;
        }
    }

    /**
     * 转发节点以明确作者标签和原片段显示，分段按原接口顺序等待持久回执。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private final class Forward implements IForwardMessageSenderBuilder {
        private final WebMessageData source;
        private final List<List<ITeaNekoContent>> nodes = new ArrayList<>();
        private Duration delay = Duration.ZERO;
        private boolean submitted;

        /** @param source 固定来源 */
        Forward(WebMessageData source) {
            this.source = source;
        }

        /** @return 原节点合并为一次受限持久消息的回执 */
        @Override
        public TaskFuture<IMessageSendResponseData> sendWithFuture() {
            reserve();
            return chunk(List.copyOf(nodes));
        }

        /**
         * @param values 本段原节点
         * @return 真实持久回执，不声明网页已读
         */
        private TaskFuture<IMessageSendResponseData> chunk(List<List<ITeaNekoContent>> values) {
            var builder = new Easy(source);
            builder.setDelay(delay);
            values.forEach(builder::addMessages);
            return builder.sendWithFuture();
        }

        /** 防止同一转发构建器重复提交或在空节点时生成假回执。 */
        private synchronized void reserve() {
            if (submitted || nodes.isEmpty()) {
                throw new IllegalStateException("转发构建器已提交或没有节点。");
            }
            submitted = true;
        }

        /**
         * @param partSize 每段节点数
         */
        @Override
        public void sendByPart(int partSize) {
            TaskFuture.fromStage(logger, "网页分段转发", sendByPartAsync(partSize)).finish();
        }

        /**
         * @param partSize 每段节点数
         * @return 全部分段已依次持久化，失败后停止后续段
         */
        @Override
        public CompletionStage<Void> sendByPartAsync(int partSize) {
            if (partSize <= 0) {
                throw new IllegalArgumentException("转发每段节点数必须为正。");
            }
            reserve();
            return MessageSendStages.sendByPart(List.copyOf(nodes), partSize,
                    part -> MessageSendStages.awaitResponse(chunk(part)));
        }

        /**
         * @param delay 原延迟
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setDelay(Duration delay) {
            new Easy(source).setDelay(delay);
            this.delay = delay;
            return this;
        }

        /**
         * @param count 原重试设置，必须为零
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setRetryCount(int count) {
            new Easy(source).setRetryCount(count);
            return this;
        }

        /**
         * @param interval 原重试间隔
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setRetryInterval(Duration interval) {
            new Easy(source).setRetryInterval(interval);
            return this;
        }

        /**
         * @param recordFailed 失败仍由原阶段报告，不自动补发
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setRecordFailed(boolean recordFailed) {
            return this;
        }

        /** @return 当前网页机器人显示名 */
        @Override
        public String getBotName() {
            return "TeaNeko";
        }

        /** @return 原网页机器人的固定平台身份 */
        @Override
        public String getBotId() {
            return WebChat.SELF;
        }

        /**
         * @param prompt 平台不提供额外外显封面
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setPrompt(String prompt) {
            return this;
        }

        /**
         * @param summary 平台不提供独立转发摘要
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setSummary(String summary) {
            return this;
        }

        /**
         * @param source 平台不提供独立转发来源封面
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder setSource(String source) {
            return this;
        }

        /**
         * @param userId 仅为原节点展示信息，不赋予身份
         * @param nickname 原节点显示名
         * @param message 原节点正文
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder addText(String userId, String nickname, String message) {
            return addList(userId, nickname, DefaultTeaNekoContentListBuilder.builder().addText(message).build());
        }

        /**
         * @param userId 原节点展示信息
         * @param nickname 原节点显示名
         * @param messageList 原节点片段
         * @return 当前构造器
         */
        @Override
        public IForwardMessageSenderBuilder addList(String userId, String nickname, List<ITeaNekoContent> messageList) {
            if (submitted || nodes.size() >= 50 || nickname.length() > 128) {
                throw new IllegalStateException("网页转发节点已提交或超过容量。");
            }
            var content = DefaultTeaNekoContentListBuilder.builder().addText("【转发：" + nickname + "】\n")
                    .addContents(messageList).build();
            nodes.add(List.copyOf(content));
            return this;
        }
    }

    /**
     * 仅在持久提交后生成的平台消息回执。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 实际消息号
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Response(String id) implements IMessageSendResponseData {
        /** @return 原持久消息号 */
        @Override
        public String getMessageId() {
            return id;
        }
    }
}
