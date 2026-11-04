package org.zexnocs.teanekoagent.component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 组件私有的不可变参数 JSON；读取生成独立节点，服务和凭据不得写入本数据。
 *
 * @param json 已校验并脱敏的参数
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public record ComponentParameters(String json) {
    /** @return 不共享可变节点的参数树。 */
    public JsonNode read() {
        return new ObjectMapper().readTree(json);
    }
}
