package org.zexnocs.teanekoapp.fake_client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 一个假的响应数据类，用于测试发送消息和接收响应的流程。
 *
 * @author zExNocs
 * @date 2026/02/23
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FakeResponseData {
    /// 数据
    @JsonProperty("data")
    private String data;
}
