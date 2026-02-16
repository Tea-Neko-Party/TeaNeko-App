package org.zexnocs.teanekoapp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试日志格式化工具
 * 用于生成美观的控制台输出，支持边框、步骤编号和多种日志级别
 */
public class TestConsoleFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HORIZONTAL_LINE = "═";
    private static final String VERTICAL_LINE = "║";
    private static final String TOP_LEFT = "╔";
    private static final String TOP_RIGHT = "╗";
    private static final String BOTTOM_LEFT = "╚";
    private static final String BOTTOM_RIGHT = "╝";
    private static final String MIDDLE_LEFT = "╠";
    private static final String MIDDLE_RIGHT = "╣";
    private static final String MIDDLE_CROSS = "╬";

    private final List<String> logLines;
    private final AtomicInteger stepCounter;
    private long testStartTime;

    private TestConsoleFormatter() {
        this.logLines = new ArrayList<>();
        this.stepCounter = new AtomicInteger(1);
    }

    /**
     * 创建新的测试格式化器
     */
    public static TestConsoleFormatter create() {
        return new TestConsoleFormatter();
    }

    /**
     * 开始测试
     */
    public TestConsoleFormatter startTest(String testName) {
        this.testStartTime = System.currentTimeMillis();

        String currentTime = LocalDateTime.now().format(TIME_FORMATTER);

        // 顶部边框
        logLines.add(buildBorder(TOP_LEFT, TOP_RIGHT, 80));
        logLines.add(buildLineWithContent("  🚀 测试启动: " + testName, 80));
        logLines.add(buildLineWithContent("  ⏱️  时间: " + currentTime, 80));
        logLines.add(buildBorder(BOTTOM_LEFT, BOTTOM_RIGHT, 80));
        logLines.add(""); // 空行

        return this;
    }

    /**
     * 添加步骤日志
     */
    public TestConsoleFormatter step(String message) {
        int stepNumber = stepCounter.getAndIncrement();
        logLines.add("  📋 " + stepNumber + ": " + message);
        return this;
    }

    /**
     * 添加信息日志
     */
    public TestConsoleFormatter info(String message) {
        logLines.add("  ℹ️  " + message);
        return this;
    }

    /**
     * 添加成功日志
     */
    public TestConsoleFormatter success(String message) {
        logLines.add("  ✅ " + message);
        return this;
    }

    /**
     * 添加警告日志
     */
    public TestConsoleFormatter warn(String message) {
        logLines.add("  ⚠️  " + message);
        return this;
    }

    /**
     * 添加错误日志
     */
    public TestConsoleFormatter error(String message) {
        logLines.add("  ❌ " + message);
        return this;
    }

    /**
     * 添加分隔线
     */
    public TestConsoleFormatter separator() {
        logLines.add("  ──────────────────────────────────────────");
        return this;
    }

    /**
     * 添加任务配置信息
     */
    public TestConsoleFormatter taskConfig(String name, String delay, String maxRetries) {
        info("任务名称: " + name);
        info("延迟: " + delay);
        info("最大重试: " + maxRetries);
        return this;
    }

    /**
     * 添加耗时信息
     */
    public TestConsoleFormatter timeCost(String message, long milliseconds) {
        success(message + "，耗时: " + milliseconds + "ms");
        return this;
    }

    /**
     * 结束测试
     */
    public TestConsoleFormatter endTest() {
        long totalTime = System.currentTimeMillis() - testStartTime;
        String endTime = LocalDateTime.now().format(TIME_FORMATTER);

        logLines.add(""); // 空行
        logLines.add(buildBorder(TOP_LEFT, TOP_RIGHT, 72));
        logLines.add(buildLineWithContent("  ✨ 测试完成", 72));
        logLines.add(buildBorder(MIDDLE_LEFT, MIDDLE_RIGHT, 72));
        logLines.add(buildLineWithContent("  总耗时: " + totalTime + " ms", 72));
        logLines.add(buildLineWithContent("  结束时间: " + endTime, 72));
        logLines.add(buildBorder(BOTTOM_LEFT, BOTTOM_RIGHT, 72));

        return this;
    }

    /**
     * 输出所有日志
     */
    public void print() {
        logLines.forEach(System.out::println);
    }

    /**
     * 构建边框线
     */
    private String buildBorder(String leftChar, String rightChar, int width) {
        return leftChar + HORIZONTAL_LINE.repeat(width - 2) + rightChar;
    }

    /**
     * 构建带内容的边框行
     */
    private String buildLineWithContent(String content, int width) {
        int contentLength = content.length();
        int paddingTotal = width - 2 - contentLength;
        int paddingLeft = 1;
        int paddingRight = paddingTotal - paddingLeft;

        return VERTICAL_LINE + " ".repeat(paddingLeft) + content + " ".repeat(paddingRight) + VERTICAL_LINE;
    }

    /**
     * 获取当前步骤号
     */
    public int getCurrentStep() {
        return stepCounter.get();
    }

    /**
     * 重置步骤计数器
     */
    public TestConsoleFormatter resetSteps() {
        stepCounter.set(1);
        return this;
    }
}