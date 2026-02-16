package org.zexnocs.teanekoapp.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;

import java.util.UUID;

/**
 * ItemData 测试类，用于测试 ItemData 相关的功能。
 * 不会在本地中删除该类，请自行删除。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@SpringBootTest
public class ItemDataTest {
    private final static UUID owner = UUID.fromString("09417781-8ebe-4eb8-9e5e-46bfbdc73e23");

    @Autowired
    private IItemDataService iItemDataService;

    /**
     * 测试减少数量不足的情况，应该抛出 InsufficientItemCountException 异常。
     * 测试时请保证 {owner, "test", "test"} 的数量不足 19。
     * 如果数据库回溯成功，应该数据库数据不变；自行检查，懒得写了（可以实现，会有点麻烦）
     */
    @Test
    public void testInsufficiency() {
        boolean[] flag = {false};
        iItemDataService.getOrCreate(owner, "test", "test", 0, null)
                .thenComposeTask(iItemData ->
                        iItemData.getDatabaseTaskConfig("测试减少数量")
                        .addCount(1)
                        .reduceCount(20)
                        .addCount(1)
                        .pushWithFuture())
                .exceptionally(t -> {
                    var unwrapped = TaskFuture.unwrapException(t);
                    Assertions.assertInstanceOf(InsufficientItemCountException.class, unwrapped);
                    flag[0] = true;
                    return null;
                })
                .finish()
                .join();
        Assertions.assertTrue(flag[0]);
    }
}
