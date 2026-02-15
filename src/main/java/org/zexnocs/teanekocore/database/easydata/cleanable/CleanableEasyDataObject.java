package org.zexnocs.teanekocore.database.easydata.cleanable;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * 可清理的 EasyData 对象。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@NoArgsConstructor
@Entity
@Table(name = "cleanable_data", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
})
public class CleanableEasyDataObject extends BaseEasyDataObject {
    protected CleanableEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
