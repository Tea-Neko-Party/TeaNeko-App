package org.zexnocs.teanekoapp.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
     */
    @Test
    public void testInsufficiency() {
        int initialCount = 4;
        boolean[] flag = {false};
        iItemDataService.getOrCreate(owner, "test", "test", 0, null).thenComposeTask(
                        iItemData -> iItemData.getDatabaseTaskConfig("设置数量")
                                .setCount(initialCount)
                                .pushWithFuture())
                .finish().join();

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
                    return null;
                })
                .finish()
                .join();
        // assert 数量没有被修改
        iItemDataService.getOrCreate(owner, "test", "test", 0, null)
                .thenAccept(iItemData -> Assertions.assertEquals(initialCount, iItemData.getCount())).finish().join();
    }

    /**
     * 测试多任务并发修改同一条数据时，应该正确处理并发冲突，最终结果应该是正确的。
     */
    @Test
    public void testConcurrentModification() {
        int taskCount = 10;
        int itemCount = 4;

        AtomicInteger counter = new AtomicInteger(0);
        iItemDataService.getOrCreate(owner, "test", "test", 0, null).thenComposeTask(
                        iItemData -> iItemData.getDatabaseTaskConfig("设置数量")
                                .setCount(itemCount)
                                .pushWithFuture())
                .finish().join();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            var future = iItemDataService.getOrCreate(owner, "test", "test", 0, null)
                    .thenComposeTask(iItemData ->
                            iItemData.getDatabaseTaskConfig("测试并发修改")
                                    .reduceCount(1)
                                    .pushWithFuture())
                    .exceptionally(t -> {
                        var unwrapped = TaskFuture.unwrapException(t);
                        Assertions.assertInstanceOf(InsufficientItemCountException.class, unwrapped);
                        counter.incrementAndGet();
                        return null;
                    })
                    .finish();
            futures.add(future);
        }
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 最终应该有 taskCount - itemCount 个任务失败，itemCount 个任务成功
        Assertions.assertEquals(taskCount - itemCount, counter.get());
        System.out.println("====================================");
    }
}
