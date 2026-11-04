package org.zexnocs.teanekoclient.agent.response;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.response.ResponseData;

/**
 * 内部客户端的标准响应信封，仅经 App ResponseService 完成关联任务。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@Getter @Setter @SuperBuilder @NoArgsConstructor
public class AgentResponseData extends ResponseData { }
