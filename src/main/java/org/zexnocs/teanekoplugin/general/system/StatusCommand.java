package org.zexnocs.teanekoplugin.general.system;

import com.sun.management.OperatingSystemMXBean;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.utils.VersionUtil;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * 状态命令，提供系统状态信息查询功能，包括 CPU 使用率、内存使用情况和磁盘使用情况。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Description("获取当前系统状态信息，包括 CPU 使用率、内存使用情况和磁盘使用情况。")
@Command(value = {"/status", "/状态"},
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL)
public class StatusCommand {
    /// CPU 使用率阈值
    private final static int CPU_THRESHOLD = 80;

    /// 内存使用率阈值（百分比）
    private final static long MEMORY_THRESHOLD = 80;

    /// os bean
    private final OperatingSystemMXBean osBean;

    /// 记录开启时间
    private final long startTime;

    /// 应用名称
    private final String applicationName;

    /// 用于监控 CPU 和内存使用率的定时任务服务
    private final ITimerService iTimerService;
    private final ILogger logger;
    private final VersionUtil versionUtil;

    public StatusCommand(ITimerService iTimerService,
                         ILogger logger,
                         @Value("${spring.application.name}") String applicationName, VersionUtil versionUtil) {
        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        this.startTime = System.currentTimeMillis();
        this.iTimerService = iTimerService;
        this.logger = logger;
        this.applicationName = applicationName;
        this.versionUtil = versionUtil;
    }

    /**
     * 初始化方法，在命令类实例化后自动调用，注册定时任务监控 CPU 和内存使用率，并在超过阈值时发送警告消息给管理员。
     *
     */
    @PostConstruct
    public void init() {
        // cpu 和 内存使用率监控。自动报警给管理员
        iTimerService.registerByDelay("cpu/内存监控", "status-monitor", () -> {
            // 检测 CPU 使用率是否超过阈值
            // 获取系统的 CPU 使用率
            double systemCpuLoad = osBean.getCpuLoad() * 100;
            if (systemCpuLoad > CPU_THRESHOLD) {
                logger.errorWithReport("status", "CPU 使用率过高: %.2f%%".formatted(systemCpuLoad));
            }
            // 检测内存使用率是否超过阈值
            // 获取系统的总内存、已用内存和空闲内存
            long totalMemory = osBean.getTotalMemorySize() + osBean.getTotalSwapSpaceSize();
            long freeMemory = osBean.getFreeMemorySize() + osBean.getFreeSwapSpaceSize();
            var memoryUsagePercentage = (1 - (double) freeMemory / totalMemory) * 100.0;
            if (memoryUsagePercentage > MEMORY_THRESHOLD) {
                logger.errorWithReport("status", """
                        内存使用率过高警告: %.2f%%""".formatted(memoryUsagePercentage));
            }
            return EmptyTaskResult.INSTANCE;
        }, Duration.ofMinutes(30), EmptyTaskResult.getResultType());
    }

    /**
     * 处理状态查询命令，获取系统的 CPU 使用率、内存使用情况和磁盘使用情况，并将结果发送给用户。
     *
     * @param commandData 命令数据，包含消息接收数据等信息
     */
    @DefaultCommand
    public void status(CommandData<ITeaNekoMessageData> commandData) {
        var data = commandData.getRawData();

        // 获取 CPU 核心数
        int cpuCount = Runtime.getRuntime().availableProcessors();
        long currentTime = System.currentTimeMillis();

        // 获取系统的 CPU 使用率
        double systemCpuLoad = osBean.getCpuLoad() * 100.0;

        // 获取物理内存使用情况
        long totalPhysicalMemory = osBean.getTotalMemorySize();
        long freePhysicalMemory = osBean.getFreeMemorySize();
        long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;
        double totalPhysicalMemoryGB = totalPhysicalMemory / (1024.0 * 1024.0 * 1024.0);
        double usedPhysicalMemoryGB = usedPhysicalMemory / (1024.0 * 1024.0 * 1024.0);
        double physicalMemoryUsagePercentage = totalPhysicalMemory == 0 ? 0 : 100.0 * usedPhysicalMemory / totalPhysicalMemory;

        // 获取虚拟内存使用情况
        long totalVirtualMemory = osBean.getTotalSwapSpaceSize();
        long freeVirtualMemory = osBean.getFreeSwapSpaceSize();
        long usedVirtualMemory = totalVirtualMemory - freeVirtualMemory;
        double totalVirtualMemoryGB = totalVirtualMemory / (1024.0 * 1024.0 * 1024.0);
        double usedVirtualMemoryGB = usedVirtualMemory / (1024.0 * 1024.0 * 1024.0);
        double virtualMemoryUsagePercentage = totalVirtualMemory == 0.0 ? 0 : 100.0 * usedVirtualMemory / totalVirtualMemory;

        // 总内存使用情况
        long totalMemory = totalPhysicalMemory + totalVirtualMemory;
        long usedMemory = usedPhysicalMemory + usedVirtualMemory;
        double totalMemoryGB = totalMemory / (1024.0 * 1024.0 * 1024.0);
        double usedMemoryGB = usedMemory / (1024.0 * 1024.0 * 1024.0);
        double memoryUsagePercentage = totalMemory == 0 ? 0 : 100.0 * usedMemory / totalMemory;

        // 构造运行时间
        long runningTimeInSeconds = (int) ((currentTime - startTime) / 1000);
        int runningDays = (int) (runningTimeInSeconds / (24 * 3600));
        int runningHours = (int) ((runningTimeInSeconds % (24 * 3600)) / 3600);
        int runningMinutes = (int) ((runningTimeInSeconds % 3600) / 60);
        int runningSeconds = (int) (runningTimeInSeconds % 60);
        String statusMessage = String.format("""
                    当前版本号: %s
                    %s 已经运行了 %d 天 %d 小时 %d 分钟 %d 秒
                    CPU 使用: %.2f%% / %d 核
                    物理内存使用率: %.2fGB / %.0f.0GB (%.2f%%)
                    虚拟内存使用率: %.2fGB / %.0f.0GB (%.2f%%)
                    总计内存使用率: %.2fGB / %.0f.0GB (%.2f%%)
                    磁盘使用率: %s""",
                versionUtil.getVersion(),
                this.applicationName,
                runningDays, runningHours, runningMinutes, runningSeconds,
                systemCpuLoad, cpuCount,
                usedPhysicalMemoryGB, totalPhysicalMemoryGB, physicalMemoryUsagePercentage,
                usedVirtualMemoryGB, totalVirtualMemoryGB, virtualMemoryUsagePercentage,
                usedMemoryGB, totalMemoryGB, memoryUsagePercentage,
                getDiskUsage());
        data.getMessageSender(CommandData.getCommandToken()).sendReplyMessage(statusMessage);
    }

    /**
     * 获取磁盘使用情况
     * @return 磁盘使用情况字符串，包含总空间、已用空间和使用率
     */
    public String getDiskUsage() {
        try {
            // 使用 df 命令显示磁盘使用情况
            ProcessBuilder processBuilder = new ProcessBuilder("df", "-h", "--output=source,size,used,avail,pcent");
            processBuilder.redirectErrorStream(true);  // 合并标准错误和标准输出

            // 启动进程
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                // 跳过第一行（标题行）
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                // 只处理文件系统挂载在 /dev/ 或其他磁盘分区的行
                if (line.startsWith("/dev/")) {
                    String[] columns = line.split("\\s+");
                    if (columns.length >= 5) {
                        // 提取总空间、已用空间、剩余空间、使用率
                        String totalSize = columns[1];   // 总空间
                        String usedSize = columns[2];    // 已用空间
                        String usePercent = columns[4];  // 使用率（百分比）

                        // 将 G 单位去掉并转换为数字（去掉 GB 后的单位）
                        double totalSizeGB = parseSizeToGB(totalSize);
                        double usedSizeGB = parseSizeToGB(usedSize);
                        double usePercentValue = Double.parseDouble(usePercent.replace("%", ""));

                        // 格式化输出
                        return String.format("%.2f / %.2f (%.2f%%)", usedSizeGB, totalSizeGB, usePercentValue);
                    }
                }
            }
            return "无法获取磁盘使用情况。"; // 如果没有匹配的磁盘分区

        } catch (Exception e) {
            return "无法获取磁盘使用情况。";
        }
    }

    /**
     * 将磁盘大小字符串转换为 GB
     * @param size 磁盘大小字符串，可能包含 G、M 或 K 后缀
     * @return 转换后的大小，以 GB 为单位
     */
    private double parseSizeToGB(String size) {
        double result = 0;
        try {
            if (size.endsWith("G")) {
                result = Double.parseDouble(size.replace("G", ""));
            } else if (size.endsWith("M")) {
                result = Double.parseDouble(size.replace("M", "")) / 1024; // 转换为 GB
            } else if (size.endsWith("K")) {
                result = Double.parseDouble(size.replace("K", "")) / (1024 * 1024); // 转换为 GB
            }
        } catch (NumberFormatException e) {
            // 如果转换失败，返回 0
            result = 0;
        }
        return result;
    }
}
