package org.zexnocs.teanekocore.database.configdata.api.default_config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 长整型默认配置数据类，表示一个长整型类型的配置项数据。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LongDefaultConfigData implements IConfigData {
    @Description("长整型")
    private long value = 0;
}
