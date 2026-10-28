package org.zexnocs.teanekocore.actuator.execution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证原子资源预占的拒绝、不溢出和并发限额。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class ResourceBudgetTest {
    private static final BudgetKey REQUESTS = new BudgetKey("requests");

    /**
     * 验证预算不足不扣费，未声明资源维度明确拒绝。
     */
    @Test
    void rejectedConsumptionDoesNotSpendAndUnknownDimensionFailsClosed() {
        var budget = new ResourceBudget(Map.of(REQUESTS, 3L));
        budget.consume(REQUESTS, 2);
        assertFalse(budget.tryConsume(REQUESTS, 2));
        assertEquals(2, budget.used(REQUESTS));
        assertEquals(1, budget.remaining(REQUESTS));
        var failure = assertThrows(BudgetExceededException.class, () -> budget.consume(REQUESTS, 2));
        assertEquals(REQUESTS, failure.key());
        assertThrows(IllegalArgumentException.class, () -> budget.consume(new BudgetKey("undeclared"), 1));
        assertThrows(IllegalArgumentException.class, () -> budget.consume(REQUESTS, 0));
    }

    /**
     * 验证大额预占不会通过整数溢出获得额外容量。
     */
    @Test
    void largeConsumptionCannotOverflowIntoAdditionalCapacity() {
        var budget = new ResourceBudget(Map.of(REQUESTS, Long.MAX_VALUE));
        budget.consume(REQUESTS, Long.MAX_VALUE - 1);
        assertFalse(budget.tryConsume(REQUESTS, 2));
        budget.consume(REQUESTS, 1);
        assertEquals(Long.MAX_VALUE, budget.used(REQUESTS));
        assertEquals(0, budget.remaining(REQUESTS));
    }

    /**
     * 验证并发预占不能超过共享预算上限。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void concurrentConsumersCannotExceedSharedLimit() throws Exception {
        var budget = new ResourceBudget(Map.of(REQUESTS, 31L));
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < 150; i++) {
                results.add(executor.submit(() -> budget.tryConsume(REQUESTS, 1)));
            }
            var accepted = 0;
            for (var result : results) {
                if (result.get(3, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(31, accepted);
            assertEquals(31, budget.used(REQUESTS));
        }
    }

    /** 验证结算只替换本句柄预留，不退还其他并发消耗，且句柄不能重复结算。 */
    @Test void settlesReservationOnceAndRefundsOnlyUnusedUnits() {
        var budget = new ResourceBudget(Map.of(REQUESTS, 10L));
        var held = budget.reserve(REQUESTS, 7);
        budget.consume(REQUESTS, 2);
        assertEquals(1, budget.remaining(REQUESTS));
        var result = held.settle(3);
        assertEquals(new ResourceBudget.Usage(10, 5), result);
        assertEquals(5, budget.remaining(REQUESTS));
        held.close();
        assertEquals(5, budget.used(REQUESTS));
        assertThrows(IllegalStateException.class, () -> held.settle(0));
    }

    /** 验证业务尚未开始时可幂等退还预留，非法结算不破坏句柄状态。 */
    @Test void closesUnusedReservationAndRejectsNegativeSettlement() {
        var budget = new ResourceBudget(Map.of(REQUESTS, 10L));
        var held = budget.reserve(REQUESTS, 7);
        assertThrows(IllegalArgumentException.class, () -> held.settle(-1));
        held.close(); held.close();
        assertEquals(0, budget.used(REQUESTS));
        assertTrue(budget.isDeclared(REQUESTS));
        assertFalse(budget.isDeclared(new BudgetKey("missing")));
    }

    /** 验证超额事实被保留，其他预留后续退款不能重新开放已封锁的维度。 */
    @Test void overrunRemainsBlockedAfterOtherReservationRefunds() {
        var budget = new ResourceBudget(Map.of(REQUESTS, 10L));
        var first = budget.reserve(REQUESTS, 4);
        var other = budget.reserve(REQUESTS, 6);
        var overrun = first.settle(5);
        assertEquals(11, overrun.used());
        assertTrue(overrun.exceeded());
        other.close();
        assertEquals(5, budget.used(REQUESTS));
        assertEquals(0, budget.remaining(REQUESTS));
        assertTrue(budget.snapshot().get(REQUESTS).exceeded());
        assertFalse(budget.tryConsume(REQUESTS, 1));
    }

    /** 验证实际消耗溢出时保留饱和与封锁标志，后续退款不会伪造较小已知总量。 */
    @Test void settlementOverflowSaturatesAndCannotReopenCapacity() {
        var budget = new ResourceBudget(Map.of(REQUESTS, Long.MAX_VALUE));
        var first = budget.reserve(REQUESTS, 1);
        var other = budget.reserve(REQUESTS, Long.MAX_VALUE - 1);
        var result = first.settle(Long.MAX_VALUE);
        assertTrue(result.overflow());
        other.close();
        assertEquals(Long.MAX_VALUE, budget.used(REQUESTS));
        assertEquals(0, budget.remaining(REQUESTS));
        assertTrue(budget.snapshot().get(REQUESTS).exceeded());
    }

    /**
     * 验证并发预留只允许总额内的请求通过，结算后共享读数无丢失。
     * @throws Exception 当受控并发任务失败时
     */
    @Test void concurrentReservationsRespectTotalAndSettleIndependently() throws Exception {
        var budget = new ResourceBudget(Map.of(REQUESTS, 20L));
        var held = new ArrayList<Future<ResourceBudget.Reservation>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                held.add(executor.submit(() -> {
                    try { return budget.reserve(REQUESTS, 2); }
                    catch (BudgetExceededException denied) { return null; }
                }));
            }
            var accepted = new ArrayList<ResourceBudget.Reservation>();
            for (var future : held) {
                var reservation = future.get(3, TimeUnit.SECONDS);
                if (reservation != null) { accepted.add(reservation); }
            }
            assertEquals(10, accepted.size());
            var settlements = new ArrayList<Future<?>>();
            for (var reservation : accepted) { settlements.add(executor.submit(() -> reservation.settle(1))); }
            for (var future : settlements) { future.get(3, TimeUnit.SECONDS); }
            assertEquals(10, budget.used(REQUESTS));
            assertEquals(10, budget.remaining(REQUESTS));
        }
    }
}
