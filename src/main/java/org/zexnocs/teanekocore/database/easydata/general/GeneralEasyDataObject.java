package org.zexnocs.teanekocore.database.easydata.general;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * 通用 EasyData 对象类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@NoArgsConstructor
@Entity
@Table(name = "easy_data", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
})
public class GeneralEasyDataObject extends BaseEasyDataObject {
    protected GeneralEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
