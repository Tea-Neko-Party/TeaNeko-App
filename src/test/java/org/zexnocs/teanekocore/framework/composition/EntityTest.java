package org.zexnocs.teanekocore.framework.composition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证组件快照、稳定类型绑定及实体版本的原子提交。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class EntityTest {
    private static final ComponentKey<Integer> COUNT = ComponentKey.of("count", Integer.class);
    private static final ComponentKey<String> LABEL = ComponentKey.of("label", String.class);

    /**
     * 验证实体快照与已构建补丁不受后续变更影响。
     */
    @Test
    void snapshotsAndBuiltPatchesAreIndependentOfLaterChanges() {
        var builder = ComponentPatch.builder().put(COUNT, 1);
        var initial = builder.build();
        builder.put(COUNT, 2).put(LABEL, "later");
        var entity = new Entity(new EntityId("first"), initial);
        var old = entity.snapshot();

        var updated = entity.commit(old.revision(), builder.build());

        assertEquals(1, old.require(COUNT));
        assertFalse(old.contains(LABEL));
        assertEquals(2, updated.require(COUNT));
        assertEquals("later", updated.require(LABEL));
        assertEquals(0, old.revision());
        assertEquals(1, updated.revision());
        assertThrows(UnsupportedOperationException.class, () -> old.keys().clear());
    }

    /**
     * 验证组件删除后仍拒绝同键不同类型。
     */
    @Test
    void conflictingTypesAreRejectedEvenAfterRemoval() {
        var entity = new Entity(new EntityId("typed"), ComponentPatch.builder().put(COUNT, 1).build());
        var wrongType = ComponentKey.of("count", String.class);
        assertThrows(IllegalArgumentException.class, () -> entity.snapshot().find(wrongType));
        assertThrows(IllegalArgumentException.class,
                () -> ComponentPatch.builder().put(COUNT, 1).put(wrongType, "wrong"));

        entity.commit(0, ComponentPatch.builder().remove(COUNT).build());

        assertFalse(entity.snapshot().contains(COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> entity.commit(1, ComponentPatch.builder().put(wrongType, "wrong").build()));
        assertEquals(1, entity.snapshot().revision());
        entity.commit(1, ComponentPatch.builder().put(COUNT, 3).build());
        assertEquals(3, entity.snapshot().require(COUNT));
    }

    /**
     * 验证非法或过期补丁不会部分修改实体。
     */
    @Test
    void invalidOrStalePatchDoesNotPartiallyChangeEntity() {
        var entity = new Entity(new EntityId("atomic"), ComponentPatch.builder().put(COUNT, 1).build());
        var invalid = ComponentPatch.builder().put(LABEL, "must not appear")
                .put(ComponentKey.of("count", String.class), "wrong type").build();

        assertThrows(IllegalArgumentException.class, () -> entity.commit(0, invalid));
        assertFalse(entity.snapshot().contains(LABEL));
        assertEquals(0, entity.snapshot().revision());

        entity.commit(0, ComponentPatch.builder().put(COUNT, 2).build());
        assertThrows(ConcurrentModificationException.class,
                () -> entity.commit(0, ComponentPatch.builder().put(COUNT, 99).build()));
        assertEquals(2, entity.snapshot().require(COUNT));
    }

    /**
     * 验证相同版本的并发写入至多一个提交成功。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void onlyOneConcurrentWriterCanCommitTheSameRevision() throws Exception {
        var entity = new Entity(new EntityId("concurrent"));
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < 24; i++) {
                var value = i;
                results.add(executor.submit(() -> {
                    assertTrue(start.await(3, TimeUnit.SECONDS));
                    try {
                        entity.commit(0, ComponentPatch.builder().put(COUNT, value).build());
                        return true;
                    } catch (ConcurrentModificationException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            var accepted = 0;
            for (var result : results) {
                if (result.get(3, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(1, entity.snapshot().revision());
        }
    }
}
