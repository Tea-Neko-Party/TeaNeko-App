package org.zexnocs.teanekocore.database.easydata.debug;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * 用于展示给人看的 EasyData 对象类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@NoArgsConstructor
@Entity
@Table(name = "debug_data", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
})
public class DebugEasyDataObject extends BaseEasyDataObject {
    protected DebugEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
