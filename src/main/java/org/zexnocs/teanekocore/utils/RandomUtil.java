package org.zexnocs.teanekocore.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 线程安全的随机数生成工具类，提供生成随机整数、布尔值和浮点数的方法。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
@Component("randomUtils")
public class RandomUtil {
    /**
     * 获取下一个随机整数。
     * 范围是 [Integer.MIN_VALUE, Integer.MAX_VALUE]。
     * @return 随机整数。
     */
    public int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    /**
     * 获取下一个随机整数。
     * 范围是 [0, bound)。
     * @param bound 上限。
     * @return 随机整数。
     */
    public int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * 获取下一个随机整数。
     * 范围是 [origin, bound)。
     * @param origin 下限。
     * @param bound 上限。
     * @return 随机整数。
     */
    public int nextInt(int origin, int bound) {
        return ThreadLocalRandom.current().nextInt(origin, bound);
    }

    /**
     * 获取下一个布尔值。
     * @return 随机布尔值。
     */
    public boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 获取下一个 double
     * 范围是 [0.0, 1.0)。
     * @return 随机浮点数。
     */
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
