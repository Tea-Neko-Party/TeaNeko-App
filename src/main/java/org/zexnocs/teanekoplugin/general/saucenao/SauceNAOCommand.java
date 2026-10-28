package org.zexnocs.teanekoplugin.general.saucenao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * SauceNAO 搜图指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("""
        使用 SauceNAO 的 api 进行搜图。
        在指令中 回复 想要搜图的图片，或者在指令后直接输入图片链接。""")
@Command(value = {"/sauceNAO", "/s搜图", "s搜图"},
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL)
@ConfigManager(
        value = "sauceNAO",
        description = "sauceNAO 的 api 配置",
        namespaces = {
                TeaNekoConfigNamespaces.GENERAL
        },
        configType = SauceNAORuleConfig.class)
public class SauceNAOCommand {
    private final IAPIResponseService apiResponseService;
    private final IConfigDataService iConfigDataService;

    /**
     * 注入 API 与配置端口，图片查询和后续结果发送沿原调用链组合。
     *
     * @param apiResponseService 由装配方提供的 {@code IAPIResponseService} 协作依赖
     * @param iConfigDataService 本次装配使用的 {@code IConfigDataService} 服务
     */
    @Autowired
    public SauceNAOCommand(IAPIResponseService apiResponseService, IConfigDataService iConfigDataService) {
        this.apiResponseService = apiResponseService;
        this.iConfigDataService = iConfigDataService;
    }

    /**
     * 串接图片查询与结果发送，失败反馈也被等待，保留原始查询异常供调用方处理。
     *
     * @param command 带原始来源的命令数据
     * @param imageUrl 用于本次处理的 {@code imageUrl} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @DefaultCommand
    public CompletionStage<Void> onQuery(CommandData<ITeaNekoMessageData> command, @DefaultValue("") String imageUrl) {
        var data = command.getRawData();
        var config = iConfigDataService.getConfigData(this, SauceNAORuleConfig.class, data.getScopeId())
                .orElse(null);
        if(config == null) {
            return data.getMessageSender()
                    .sendReplyMessageAsync("当前范围未开启 SauceNAO 搜图功能。");
        }
        return _onQuery(data, imageUrl, config);
    }

    /**
     * 处理搜图请求。
     *
     * @param data 消息数据
     * @param imageUrl 图片链接（可选，如果未提供则尝试从回复的消息中获取）
     * @param config 配置数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> _onQuery(ITeaNekoMessageData data, String imageUrl, SauceNAORuleConfig config) {
        var api = config.getApi();
        // 判断 api
        if(api == null || api.isBlank()) {
            return data.getMessageSender()
                    .sendReplyMessageAsync("未配置 SauceNAO API。");
        }
        // 判断 url
        if(imageUrl.isBlank() || StringUtils.Instance.isValidUrl(imageUrl)) {
            // 如果没有提供图片链接，则尝试从回复的消息中获取图片
            IReplyTeaNekoContentPart replyContext = null;
            for(var message: data.getMessages()) {
                var context = message.getContentPart();
                if(context instanceof IReplyTeaNekoContentPart) {
                    replyContext = (IReplyTeaNekoContentPart) context;
                    break;
                }
            }
            if(replyContext == null) {
                return data.getMessageSender()
                        .sendReplyMessageAsync("指令使用错误：未输入正确的图片URL地址 / 未回复信息");
            }
            return data.getClient().getTeaNekoToolbox()
                    .getGetMsgSender()
                    .getMsg(replyContext.getId())
                    .thenCompose(result -> {
                        var msg = result.getResult();
                        if(!result.isSuccess() || msg == null) {
                            return data.getMessageSender()
                                    .sendReplyMessageAsync("获取回复消息失败，可能该消息已被撤回。");
                        }
                        IImageTeaNekoContentPart imageContent = null;
                        for(var message: msg.getMessages()) {
                            var context = message.getContentPart();
                            if(context instanceof IImageTeaNekoContentPart) {
                                imageContent = (IImageTeaNekoContentPart) context;
                                break;
                            }
                        }
                        if(imageContent == null) {
                            return data.getMessageSender()
                                    .sendReplyMessageAsync("回复的消息中未包含图片。");
                        }
                        var url = imageContent.getUrl();
                        return _queryImage(data, api, url, config);
                    }).asStage();
        } else {
            // 如果提供了图片链接，则直接使用该链接进行查询
            return _queryImage(data, api, imageUrl, config);
        }
    }

    /**
     * 执行图片查询。
     *
     * @param data 消息数据
     * @param api  SauceNAO API 密钥
     * @param imageUrl 图片链接
     * @param config 配置数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> _queryImage(ITeaNekoMessageData data, String api, String imageUrl, SauceNAORuleConfig config) {
        var requestData = SauceNAORequestData.builder()
                .apiKey(api)
                .url(imageUrl)
                .dbmask(String.valueOf(config.getEnableIndex()))
                .numres(config.getMaxImageCount())
                .hide(config.getHide())
                .build();
        return apiResponseService.addTask(requestData, SauceNAOResponseData.class)
                .thenCompose(result -> {
                    var minMatchingScore = config.getMinSimilarity();
                    var header = result.getHeader();
                    var results = result.getResults();
                    if(results == null) {
                        // 防止 results 为 null 的情况
                        results = new ArrayList<>();
                    }
                    boolean success = header.getStatus() == 0;
                    double maxSimilarity = 0.0;
                    var builder = data.getForwardMessageSender();
                    int size = 0;
                    for(var resultData: results) {
                        var similarity = Double.parseDouble(resultData.getHeader().getSimilarity());
                        if(similarity > maxSimilarity) {
                            maxSimilarity = similarity;
                        }
                        if(similarity < minMatchingScore) {
                            // 如果相似度小于最小匹配度，则跳过
                            continue;
                        }
                        var resultHeader = resultData.getHeader();
                        var messageBuilder = data.getClient().getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder();
                        if(config.isPreview()) {
                            messageBuilder.addImage(resultHeader.getThumbnail());
                        }
                        messageBuilder.addText(String.format("""
                               相似度: %s%%
                               来源：%s""",
                                resultHeader.getSimilarity(),
                                resultHeader.getIndex_name()));
                        var extUrls = resultData.getData().get("ext_urls");
                        if(extUrls instanceof String) {
                            messageBuilder.addText("\n链接：" + extUrls);
                        } else if(extUrls instanceof List<?> list) {
                            messageBuilder.addText("\n链接列表：");
                            for(int i = 0; i < list.size(); i++) {
                                var url = list.get(i);
                                messageBuilder.addText("\n" + (i + 1) + ". " + url);
                            }
                        }
                        builder.addBotList(messageBuilder.build());
                        size++;
                    }
                    var summary = String.format("""
                            == 搜索结果 ==
                            状态：%s
                            查询图片数量：%d
                            高于 %d%% 的结果数量：%d
                            最高相似度：%.2f%%
                            今日剩余查询次数：%d""",
                            success ? "成功" : "失败{" + header.getStatus() + "}",
                            header.getResults_returned(),
                            minMatchingScore, size,
                            maxSimilarity,
                            header.getLong_remaining());
                    boolean forward = success && !results.isEmpty();
                    return data.getMessageSender().sendReplyMessageAsync(summary)
                            .thenCompose(ignored -> forward ? builder.sendByPartAsync(8) : CompletableFuture.completedStage(null));
                }).asStage().exceptionallyCompose(error -> {
                    Throwable source = error;
                    while (source != null && !(source instanceof WebClientResponseException)) source = source.getCause();
                    CompletionStage<Void> feedback;
                    if (source == null) {
                        feedback = data.getMessageSender().sendReplyMessageAsync("查询失败，未知报错信息。");
                    } else {
                        var builder = data.getForwardMessageSender();
                        for (var stack : source.getStackTrace()) builder.addBotText(stack.toString());
                        feedback = data.getMessageSender().sendReplyMessageAsync("查询失败，报错信息：")
                                .thenCompose(ignored -> builder.sendByPartAsync(8));
                    }
                    return feedback.handle((ignored, feedbackError) -> {
                        if (feedbackError != null && feedbackError != error) error.addSuppressed(feedbackError);
                        throw new java.util.concurrent.CompletionException(error);
                    });
                });
    }
}
