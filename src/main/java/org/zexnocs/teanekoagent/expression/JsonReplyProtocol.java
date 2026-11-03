package org.zexnocs.teanekoagent.expression;
import org.springframework.stereotype.Component;
/**
 * 旧 JSON 回复版本一的明确兼容注册，沿用原严格校验。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoReplyProtocol("teaneko:json-reply")
public final class JsonReplyProtocol extends PlainReplyProtocol {}
