package org.zexnocs.teanekocore.database.itemdata.metadata;

/**
 * 空的物品元数据类，用于没有特殊元数据的物品。
 * <br> 建议使用该 metadata 作为空 metadata 的默认值，以避免 {@code null} 引起的空指针异常。
 * <br> 尽管程序中已经尽可能适配 {@code null} 的情况，但可能会出现意想不到的 bug。
 *
 * @author zExNocs
 * @date 2026/03/10
 * @since 4.1.3
 */
@ItemMetadata("null")
public enum EmptyItemMetadata implements IItemMetadata {
    Instance
}
