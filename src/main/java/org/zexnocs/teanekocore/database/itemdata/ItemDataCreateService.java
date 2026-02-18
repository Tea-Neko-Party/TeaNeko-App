package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCreateService;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import org.zexnocs.teanekocore.database.itemdata.metadata.ItemMetadataScanner;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 物品数据创建服务实现类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemDataCreateService implements IItemDataCreateService {
    private final ItemDataRepository itemDataRepository;
    private final ObjectMapper objectMapper;
    private final ItemMetadataScanner itemMetadataScanner;

    public ItemDataCreateService(ItemDataRepository itemDataRepository,
                                 ItemMetadataScanner itemMetadataScanner) {
        this.itemDataRepository = itemDataRepository;
        this.itemMetadataScanner = itemMetadataScanner;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 在数据库事务中创建一个新的物品数据记录
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @param count 初始数量
     * @param metadata 物品元数据
     * @return 新创建的物品数据传输对象
     */
    @Override
    @Transactional
    public ItemDataObject createIfAbsent(UUID ownerId,
                                         String namespace,
                                         String type,
                                         int count,
                                         IItemMetadata metadata)
            throws ClassCastException, InvalidMetadataTypeException, JacksonException {
        // 判断数据库里是否已经存在该物品数据
        var existing = itemDataRepository.findByOwnerIdAndNamespaceAndType(ownerId, namespace, type);
        if(existing != null) {
            return existing;
        }
        // 解析物品元数据
        String metadataJson;
        String metadataType;
        if(metadata != null) {
            metadataJson = objectMapper.writeValueAsString(metadata);
            metadataType = itemMetadataScanner.getTypeFromClass(metadata.getClass());
            if(metadataType == null) {
                throw new InvalidMetadataTypeException("""
                    无法识别的物品元数据类型：%s""".formatted(metadata.getClass().getName()));
            }
        } else {
            metadataJson = null;
            metadataType = null;
        }
        // 构造并保存物品数据对象
        var itemDataObject = ItemDataObject.builder()
                .ownerId(ownerId)
                .namespace(namespace)
                .type(type)
                .metadataType(metadataType)
                .metadata(metadataJson)
                .count(count)
                .build();
        return itemDataRepository.save(itemDataObject);
    }
}
