package org.zexnocs.teanekoagent.expression;
import org.springframework.stereotype.Component;
/**
 * 旧 JSON 回复版本一的明确兼容注册，沿用原严格校验。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyProtocol("teaneko:json-reply")
public final class JsonReplyProtocol extends PlainReplyProtocol {}
