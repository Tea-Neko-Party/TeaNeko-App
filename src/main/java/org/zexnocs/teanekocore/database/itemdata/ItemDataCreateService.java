package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCreateService;
import org.zexnocs.teanekocore.database.itemdata.metadata.ItemMetadataScanner;

/**
 * 物品数据创建服务实现类。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 * @version 4.1.3
 */
@Service
public class ItemDataCreateService implements IItemDataCreateService {
    private final ItemDataRepository itemDataRepository;
    private final ItemMetadataScanner itemMetadataScanner;

    public ItemDataCreateService(ItemDataRepository itemDataRepository,
                                 ItemMetadataScanner itemMetadataScanner) {
        this.itemDataRepository = itemDataRepository;
        this.itemMetadataScanner = itemMetadataScanner;
    }

    /**
     * 在数据库事务中创建一个新的物品数据记录。
     * <br>默认数量为 0，metadata 为 null。
     *
     * @param dto 物品数据传输对象，包含了物品的 uuid、数量和元数据等信息。
     * @return 新创建的物品数据传输对象
     * @throws InvalidMetadataTypeException 无效的物品元数据类型异常
     */
    @Override
    @Transactional
    public ItemDataObject createIfAbsent(ItemDataDTO<?> dto) throws InvalidMetadataTypeException {
        // 判断数据库里是否已经存在该物品数据
        var ownerId = dto.getOwnerId();
        var namespace = dto.getNamespace();
        var type = dto.getType();
        var clazz = dto.getMetadataClass();

        // 优先从数据库中查找，如果已经存在则直接返回，不存在才创建新的记录。
        var existing = itemDataRepository.findByOwnerIdAndNamespaceAndType(ownerId, namespace, type);
        if(existing != null) {
            return existing;
        }
        var metadataType = itemMetadataScanner.getTypeFromClass(clazz);
        // 构造并保存物品数据对象
        var itemDataObject =
                ItemDataObject.builder()
                        .ownerId(ownerId)
                        .namespace(namespace)
                        .type(type)
                        .metadataType(metadataType)
                        .metadata(null)
                        .count(dto.getCount())
                        .build();
        return itemDataRepository.save(itemDataObject);
    }
}
