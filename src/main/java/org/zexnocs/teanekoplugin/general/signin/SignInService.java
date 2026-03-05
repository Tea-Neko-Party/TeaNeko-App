package org.zexnocs.teanekoplugin.general.signin;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserCoinService;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.signin.data.SignInChunkData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInRecordData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 签到服务
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.1.0
 */
@Service
public class SignInService {
    /// 存储签到数据的命名空间
    protected static final String NAMESPACE = "sign_in";

    /// 签到领取的最小金币数
    private static final int MIN_COIN = 10;

    /// 签到领取的最大金币数
    private static final int MAX_COIN = 50;

    /// 用来缓存用户签到，防止用户重复签到
    private final Map<UUID, Boolean> todaySignInSet = new ConcurrentHashMap<>();
    private final ITimerService iTimerService;
    private final RandomUtil randomUtil;
    private final ITeaUserCoinService iTeaUserCoinService;

    public SignInService(ITimerService iTimerService, RandomUtil randomUtil, ITeaUserCoinService iTeaUserCoinService) {
        this.iTimerService = iTimerService;
        this.randomUtil = randomUtil;
        this.iTeaUserCoinService = iTeaUserCoinService;
    }

    /**
     * 初始化，制造一个定时器每天零点准时清理 {@code todaySignInSet}
     *
     */
    @PostConstruct
    public void _init() {
        iTimerService.registerByCron(
                "重置签到缓存",
                "CLEAN-SIGN-IN-SET",
                () -> {
                    todaySignInSet.clear();
                    return EmptyTaskResult.INSTANCE;
                },
                "0 0 0 * * ?",
                EmptyTaskResult.getResultType());
    }

    /**
     * 签到
     *
     * @param userId 用户 ID
     * @param nowMs 当前时间戳（毫秒）
     * @return {@link String } 要发送的消息
     */
    public String signIn(UUID userId, long nowMs) {
        // 获取当前时间和日期
        var currentDate = ChinaDateUtil.Instance.convertToChinaDate(nowMs);

        // 获取用户的签到数据
        var target = GeneralEasyData.of(NAMESPACE).get(userId.toString());
        var data = getSignInData(target);
        // 检查用户是否在缓存中已经签到过了，如果是则直接返回签到过的消息
        String repeatMsg = checkRepeatSignIn(userId, currentDate, data);
        if (repeatMsg != null) {
            return repeatMsg;
        }

        // 计算签到奖励
        int luckyNumber = generateLuckyNumber();
        int coin = calculateCoin(currentDate, luckyNumber);
        // 获取当前连续签到天数
        var chunks = new LinkedList<>(target.getList(SignInChunkData.KEY, SignInChunkData.class));
        int continuous = updateChunks(chunks, currentDate, nowMs);
        // 构建新的签到数据和签到记录
        var newData = buildNewSignInData(data, nowMs, luckyNumber, continuous);
        var record = buildRecord(nowMs, luckyNumber, coin);
        // 更新数据库
        pushDatabaseUpdate(userId, target, chunks, newData, record, coin, currentDate);
        // 构建并返回签到成功消息
        return buildSuccessMessage(currentDate, nowMs, luckyNumber, coin, continuous, newData.getTotalDays());
    }

    /**
     * 获取签到数据，如果不存在则返回一个默认的签到数据对象。
     *
     * @param target 签到数据所在的 EasyDataDto 对象
     * @return {@link SignInData } 签到数据
     */
    private SignInData getSignInData(IEasyDataDto target) {
        return target.get(SignInData.KEY,
                SignInData.builder()
                        .totalDays(0)
                        .lastTimeMs(0)
                        .lastNumber(0)
                        .continuousDays(0)
                        .build());
    }

    /**
     * 获取签到记录块列表，如果不存在则返回一个空列表。
     *
     * @param userId 用户 ID
     * @param currentDate 当前日期
     * @param data 签到数据
     * @return {@link String }
     */
    private String checkRepeatSignIn(UUID userId,
                                     LocalDate currentDate,
                                     SignInData data) {
        var lastDate = ChinaDateUtil.Instance.convertToChinaDate(data.getLastTimeMs());
        // 使用缓存来判断用户是否已经签到过了，如果 put 返回的不是 null，说明用户之前已经签到过了。
        if (todaySignInSet.put(userId, true) != null) {
            // 如果用户上次签到的日期不是今天，说明数据库还没更新，用户签到过快。
            if (!currentDate.isEqual(lastDate)) {
                return "主人您签到太快了喵！";
            }
            // 否则返回已经签到过的消息
            return """
               主人您今天已经签到过了喵！
               您今天的幸运数字是 %d 喵！
               您已经连续签到 %d 天喵！
               您已经累计签到 %d 天喵！"""
                    .formatted(data.getLastNumber(),
                            data.getContinuousDays(),
                            data.getTotalDays());
        }
        // 使用 data 中的最后一次签到时间来判断用户是否已经签到过了
        // 只有在 签到 → 服务器重启 → 用户再次签到 情况下才会出现用户签到过了但是缓存里没有的情况
        if(currentDate.isEqual(lastDate)) {
            return """
               主人您今天已经签到过了喵！
               您今天的幸运数字是 %d 喵！
               您已经连续签到 %d 天喵！
               您已经累计签到 %d 天喵！"""
                    .formatted(data.getLastNumber(),
                            data.getContinuousDays(),
                            data.getTotalDays());
        }
        return null;
    }

    /**
     * 生成一个随机的幸运数字，范围在 1 到 100 之间。
     *
     * @return int 幸运数字
     */
    private int generateLuckyNumber() {
        return randomUtil.nextInt(100) + 1;
    }

    /**
     * 根据当前日期和幸运数字计算签到奖励的猫猫币数量。
     * 如果当前日期是周四，则奖励固定为 50 猫猫币；
     * 否则根据幸运数字在 MIN_COIN 和 MAX_COIN 之间线性计算奖励。
     *
     * @param date 当前日期
     * @param luckyNumber 幸运数字
     * @return int
     */
    private int calculateCoin(LocalDate date, int luckyNumber) {
        if (date.getDayOfWeek() == DayOfWeek.THURSDAY) {
            return 50;
        }
        return (int) (MIN_COIN + (MAX_COIN - MIN_COIN) * luckyNumber / 100.0);
    }

    /**
     * 构建新的签到数据对象，包含更新后的总签到天数、最后一次签到时间、幸运数字和连续签到天数。
     *
     * @param chunks 签到记录块列表
     * @param currentDate 当前日期
     * @param now 当前时间戳
     * @return int 当前连续签到天数
     */
    private int updateChunks(List<SignInChunkData> chunks,
                             LocalDate currentDate,
                             long now) {
        int continuous = 1;
        if (!chunks.isEmpty()) {
            var last = chunks.getLast();
            if (currentDate.minusDays(1)
                    .isEqual(ChinaDateUtil.Instance.convertToChinaDate(last.getLastTimeMs()))) {
                continuous = last.getContinuous() + 1;
                chunks.removeLast();
            }
        }
        chunks.addLast(SignInChunkData.builder()
                .lastTimeMs(now)
                .continuous(continuous)
                .build());
        return continuous;
    }

    /**
     * 构建签到记录对象，包含签到时间、幸运数字和获得的猫猫币数量。
     *
     * @param data 签到数据
     * @param now 当前时间戳
     * @param luckyNumber 幸运数字
     * @param continuous 当前连续签到天数
     * @return {@link SignInData } 签到数据
     */
    private SignInData buildNewSignInData(SignInData data,
                                          long now,
                                          int luckyNumber,
                                          int continuous) {

        return data.toBuilder()
                .totalDays(data.getTotalDays() + 1)
                .lastTimeMs(now)
                .lastNumber(luckyNumber)
                .continuousDays(continuous)
                .build();
    }

    /**
     * 构建签到成功消息，包含签到时间、幸运数字、获得的猫猫币数量、连续签到天数和累计签到天数。
     *
     * @param now 当前时间戳
     * @param luckyNumber 幸运数字
     * @param coin 获得的猫猫币数量
     * @return {@link SignInRecordData }
     */
    private SignInRecordData buildRecord(long now,
                                         int luckyNumber,
                                         int coin) {

        return SignInRecordData.builder()
                .time(ChinaDateUtil.Instance.convertToDateTimeString(now))
                .coin(coin)
                .luckyNumber(luckyNumber)
                .build();
    }

    /**
     * 将更新后的签到数据、签到记录和用户猫猫币数量推送到数据库中。
     *
     */
    private void pushDatabaseUpdate(UUID userId,
                                    IEasyDataDto target,
                                    List<SignInChunkData> chunks,
                                    SignInData newData,
                                    SignInRecordData record,
                                    int coin,
                                    LocalDate currentDate) {

        var targetTask = target.getTaskConfig("签到数据更新")
                .set(SignInData.KEY, newData)
                .set(SignInChunkData.KEY, chunks);

        var recordTask = CleanableEasyData.of(NAMESPACE)
                .get(userId.toString())
                .getTaskConfig("签到记录数据更新")
                .set(ChinaDateUtil.Instance.convertToString(currentDate), record);

        iTeaUserCoinService.getCoin(userId)
                .thenComposeTask(coinItem ->
                        coinItem.getDatabaseTaskConfig("签到金币更新")
                                .addCount(coin)
                                .merge(targetTask)
                                .merge(recordTask)
                                .pushWithFuture())
                .finish();
    }

    /**
     * 构建签到成功消息，包含签到时间、幸运数字、获得的猫猫币数量、连续签到天数和累计签到天数。
     *
     */
    private String buildSuccessMessage(LocalDate date,
                                       long now,
                                       int luckyNumber,
                                       int coin,
                                       int continuous,
                                       int totalDays) {
        var extra = date.getDayOfWeek() == DayOfWeek.THURSDAY
                ? "疯狂星期四v你 50 喵！\n" : "";
        return """
        %s主人签到成功喵！
        您今日签到时间：%s 喵
        您的幸运数字：%d 喵 [%d, %d]
        您获得了猫猫币：%d 喵 [%d, %d]
        您已经连续签到：%d 天
        您已经累计签到：%d 天"""
                .formatted(
                        extra,
                        ChinaDateUtil.Instance.convertToDateTimeString(now),
                        luckyNumber, 1, 100,
                        coin, MIN_COIN, MAX_COIN,
                        continuous,
                        totalDays);
    }
}
