package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTOTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataConfigService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import tools.jackson.databind.ObjectMapper;

/**
 * 物品数据配置服务实现类
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemDataConfigService implements IItemDataConfigService {
    private final IDatabaseService iDatabaseService;
    private final ItemDataRepository itemDataRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ItemDataConfigService(IDatabaseService iDatabaseService,
                                 ItemDataRepository itemDataRepository) {
        this.iDatabaseService = iDatabaseService;
        this.itemDataRepository = itemDataRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 构建物品数据传输对象任务配置
     * @param itemDataDTO 物品数据传输对象
     * @param taskName 任务名称
     * @return 物品数据传输对象任务配置
     * @param <T> 物品元数据类型
     */
    @Override
    public <T extends IItemMetadata> IItemDataDtoTaskConfig<T> buildDatabaseTaskConfig(ItemDataDTO<T> itemDataDTO,
                                                                                       String taskName) {
        return new ItemDataDTOTaskConfig<>(
                iDatabaseService,
                itemDataRepository,
                objectMapper,
                itemDataDTO,
                taskName);
    }
}
