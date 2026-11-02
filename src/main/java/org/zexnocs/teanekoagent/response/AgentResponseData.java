package org.zexnocs.teanekoagent.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.response.ResponseData;

/**
 * 内部客户端的标准响应信封，仅经 App ResponseService 完成关联任务。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@Getter @Setter @SuperBuilder @NoArgsConstructor
public class AgentResponseData extends ResponseData { }
