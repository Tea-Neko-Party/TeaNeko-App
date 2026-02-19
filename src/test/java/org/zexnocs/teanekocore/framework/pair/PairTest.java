package org.zexnocs.teanekocore.framework.pair;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pair 测试类，用于测试 Pair 相关的功能。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@SpringBootTest
public class PairTest {

    /**
     * 测试 HashPair 的 equals 方法。
     * 理论上来说，HashPair 的 equals 方法应该比较两个 HashPair 的 key 和 value 是否相等，而不是比较它们的引用地址。
     * 因此，如果两个 HashPair 的 key 和 value 都相同，那么它们应该被认为是相等的。
     *
     */
    @Test
    public void testHashPair() {
        var a = HashPair.of("a", "b");
        var b = HashPair.of("a", "b");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * 测试 IndependentPair 的 equals 方法。
     * 理论上来说，IndependentPair 的 equals 方法应该比较两个 IndependentPair 的引用地址，而不是比较它们的 key 和 value 是否相等。
     * 因此，如果两个 IndependentPair 的 key 和 value 都相同，但它们是不同的对象，那么它们应该被认为是不相等的。
     */
    @Test
    public void testIndependentPair() {
        String a = "a";
        String b = "b";
        var pairs = new IndependentPair[4];
        pairs[0] = IndependentPair.of("a", "b");
        pairs[1] = IndependentPair.of("a", "b");
        pairs[2] = IndependentPair.of(a, b);
        pairs[3] = IndependentPair.of(a, b);
        for (int i = 0; i < pairs.length; i++) {
            for (int j = 0; j < pairs.length; j++) {
                if (i != j) {
                    assertNotEquals(pairs[i], pairs[j]);
                    assertNotEquals(pairs[i].hashCode(), pairs[j].hashCode());
                }
            }
        }
    }

    /**
     * 测试 UnorderedPair 的 equals 方法。
     * 理论上来说，UnorderedPair 的 equals 方法应该比较两个 UnorderedPair 的 key 和 value 是否相等，而不考虑它们的顺序。
     * 因此，如果两个 UnorderedPair 的 key 和 value 都相同，无论它们的顺序如何，那么它们应该被认为是相等的。
     */
    @Test
    public void testUnorderedPair() {
        var a = UnorderedPair.of("a", "b");
        var b = UnorderedPair.of("b", "a");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
