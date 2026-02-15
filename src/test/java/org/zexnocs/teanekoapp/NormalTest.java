package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Test
    void run() {
        var a = HashPair.of("a", "b");
        var b = HashPair.of("a", "b");
        assert a.equals(b);

        var c = IndependentPair.of("a", "b");
        var d = IndependentPair.of("a", "b");
        assert !c.equals(d);
    }
}
