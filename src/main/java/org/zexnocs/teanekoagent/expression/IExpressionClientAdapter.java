package org.zexnocs.teanekoagent.expression;

import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 客户端明确声明可用能力；不能把本地路径交给远程机器人读取。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public interface IExpressionClientAdapter{
    /**
     * @param data 原目标
     * @return 是否可发送内联图片
     */ boolean images(ITeaNekoMessageData data);
    /**
     * @param data 原目标
     * @param settings 本地媒体设置
     * @return 稳定不透明 ID 与描述
     */ Map<String,String> faces(ITeaNekoMessageData data,MediaReplySettings settings);
    /**
     * @param data 原目标
     * @param base64 已验证原图字节的编码
     * @param mode plain 或 reply
     * @return 真实平台确认
     */ CompletionStage<Void> image(ITeaNekoMessageData data,String base64,Mode mode);
    /**
     * @param data 原目标
     * @param id 已在目录提供的标识
     * @param mode plain 或 reply
     * @return 真实平台确认
     */ CompletionStage<Void> face(ITeaNekoMessageData data,String id,Mode mode);
}
