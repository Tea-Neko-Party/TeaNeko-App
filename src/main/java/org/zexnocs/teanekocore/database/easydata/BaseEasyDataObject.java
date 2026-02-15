package org.zexnocs.teanekocore.database.easydata;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 抽象的储存在 EasyData 类数据库中的对象
 * 该类和子类具有 setter，是不安全的，不应该直接使用。
 * 请继承这个类并：
 * 1. 使用 @Entity 注解
 * 2. 使用 @Table 注解来指定表名
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public abstract class BaseEasyDataObject {
    /**
     * 构造函数。
     * ID 由数据库自动生成。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @param value 值。
     */
    public BaseEasyDataObject(String namespace, String target, String key, String value) {
        this.namespace = namespace;
        this.target = target;
        this.key = key;
        this.value = value;
    }

    /**
     * 数据库中的id。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private long id;

    /**
     * 数据的命名空间。
     */
    @Column(name = "namespace", nullable = false)
    private String namespace;

    /**
     * 要储存数据的对象/目标。
     */
    @Column(name = "target", nullable = false)
    private String target;

    /**
     * 数据的键。
     */
    @Column(name = "o_key", nullable = false)
    private String key;

    /**
     * 数据的值。
     */
    @Column(name = "o_value", nullable = false, columnDefinition = "LONGTEXT")
    private String value;

    /**
     * 乐观锁
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
