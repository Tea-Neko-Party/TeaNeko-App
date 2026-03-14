package org.zexnocs.teanekoplugin.onebot.request.review;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoclient.onebot.data.receive.request.GroupRequestData;
import org.zexnocs.teanekoclient.onebot.data.response.params.StrangerInfoGetResponseData;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageListBuilder;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekoplugin.onebot.servant.GroupSeniorServantRule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用于管理、缓存群聊入群请求审核服务。
 *
 * @author zExNocs
 * @date 2026/03/10
 * @since 4.1.3
 */
@Service("GroupRequestService")
public class GroupRequestReviewService {
    private final static String TASK_NAMESPACE = "group-request-service-cleaner";
    /// 用于注册自动清理过期请求的定时器，单位为毫秒。默认为 1 小时。
    private final ITimerService timerService;

    /// 用于发送入群请求审核结果的发送器。
    private final GroupAddRequestSender groupAddRequestSender;

    /// 存储入群请求数据的 Map，key 为 (groupId, userId)，value 为 GroupRequestValue。
    private final Map<HashPair<Long, Long>, GroupRequestValue> requestMap = new ConcurrentHashMap<>();

    /// 存储每个群聊的上次请求ID的 Map，key 为群聊ID，value 为上次请求者的ID。
    private final Map<Long, Long> lastRequestIdMap = new ConcurrentHashMap<>();

    /// 群管理员规则，用于判断操作者是否为管理员。
    private final GroupSeniorServantRule groupSeniorServantRule;

    @Autowired
    public GroupRequestReviewService(ITimerService timerService,
                                     GroupAddRequestSender groupAddRequestSender,
                                     GroupSeniorServantRule groupSeniorServantRule) {
        this.groupAddRequestSender = groupAddRequestSender;
        this.timerService = timerService;
        this.groupSeniorServantRule = groupSeniorServantRule;
    }

    /**
     * 创建定时器，定时清理过期的请求。
     */
    @PostConstruct
    public void init() {
        timerService.registerByDelay("清理过期的入群请求",
                TASK_NAMESPACE,
                this::__clean,
                Duration.ofHours(60),
                EmptyTaskResult.getResultType());
    }

    /**
     * 清理过期的入群请求。
     */
    public EmptyTaskResult __clean() {
        requestMap.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return EmptyTaskResult.INSTANCE;
    }

    /**
     * 获取上次的请求ID。
     *
     * @return 上次的请求ID
     */
    public long getLastRequestId(long group) {
        return lastRequestIdMap.getOrDefault(group, 0L);
    }

    /**
     * 添加新的入群请求。
     *
     * @param requestData 入群请求数据
     * @param strangerInfo 请求者的资料
     */
    public void addRequest(GroupRequestData requestData,
                           @Nullable StrangerInfoGetResponseData strangerInfo,
                           int requestAcceptNum,
                           int requestRejectNum) {
        requestMap.put(HashPair.of(requestData.getGroupId(), requestData.getUserId()),
                new GroupRequestValue(requestData, strangerInfo, requestAcceptNum, requestRejectNum));
        lastRequestIdMap.put(requestData.getGroupId(), requestData.getUserId());
    }

    /**
     * 删除入群请求。
     * @param groupId 群聊ID
     * @param userId 请求者ID
     * @return 是否成功删除请求
     */
    public boolean removeRequest(long groupId, long userId) {
        var key = HashPair.of(groupId, userId);
        return requestMap.remove(key) != null;
    }

    /**
     * 展示一个详细的入群请求。
     *
     * @param groupId 群聊ID
     * @param userId  请求者ID
     * @return 返回发送的消息列表。即一个 TextMessage。如果没有请求数据，则返回 null。
     */
    public @NonNull List<ITeaNekoMessage> showOneDetailRequest(long groupId, long userId) {
        var key = HashPair.of(groupId, userId);
        var value = requestMap.get(key);
        if (value == null) {
            return OnebotMessageListBuilder
                    .builder()
                    .addTextMessage("该请求不存在喵")
                    .build();
        }
        var builder = OnebotMessageListBuilder.builder();
        var invitorId = value.requestData.getInvitorId();
        builder.addTextMessage(String.format("""
                请求者的QQ号：%d
                邀请者的QQ号：%s
                """, userId, invitorId == 0 ? "无" : invitorId));
        var info = value.strangerInfo;
        if(info != null) {
            var date = new Date(info.getRegTime() * 1000);
            var sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            String regTimeStr = sdf.format(date);
            builder.addImageMessage(AvatarUtils.Instance.getAvatarUrl(userId))
                    .addTextMessage(String.format("""
                            昵称: %s
                            性别: %s
                            年龄: %d
                            等级: %d
                            注册时间: %s""",
                            info.getNickname(),
                            info.getSex().equals("male") ? "男" : info.getSex().equals("female") ? "女" : "未知",
                            info.getAge(),
                            info.getLevel(),
                            regTimeStr));
        } else {
            builder.addTextMessage("""
                    请求者的资料获取失败了喵。""");
        }
        builder.addTextMessage(String.format("""
                
                
                请求者申请信息喵：
                %s
                
                需 %s 猫同意：/gr 接受 %s
                需 %s 猫拒绝：/gr 拒绝 %s""",
                value.requestData.getComment(),
                value.requestAcceptNum, userId,
                value.requestRejectNum, userId));
        return builder.build();
    }

    /**
     * 展示一个详细的所有入群请求。
     * @param groupId 群聊ID
     * @return 返回发送的消息列表，用于 {@link org.zexnocs.teanekoapp.message.api.content.INodeTeaNekoContent} 中的 content。
     */
    @NonNull
    public List<List<ITeaNekoMessage>> showAllDetailRequest(long groupId) {
        var list = new ArrayList<List<ITeaNekoMessage>>();
        requestMap.keySet().forEach(key -> {
            if(key.first() == groupId) {
                list.add(showOneDetailRequest(key.first(), key.second()));
            }
        });
        if(list.isEmpty()) {
            return List.of(OnebotMessageListBuilder.builder().addTextMessage("暂无入群请求喵。").build());
        }
        return list;
    }

    /**
     * 拒绝入群请求。
     *
     * @param groupId 群聊ID
     * @param userId  请求者ID
     * @param opId    操作者ID
     */
    @Nullable
    public List<ITeaNekoMessage> reject(long groupId, long userId, long opGroupId, long opId) {
        var key = HashPair.of(groupId, userId);
        var value = requestMap.get(key);
        // 如果不存在请求数据
        if (value == null) {
            return OnebotMessageListBuilder.builder().addTextMessage("该请求不存在喵").build();
        }
        // 如果已经处理过该请求
        if (value.isHandled(opId)) {
            return OnebotMessageListBuilder.builder().addTextMessage("你已经处理过该请求了喵").build();
        }
        int rejectNum = value.reject(opGroupId, opId);

        if (rejectNum >= value.requestRejectNum) {
            groupAddRequestSender.reject(
                    value.getRequestData().getFlag(),
                    "群成员投票拒绝了您的入群请求。"
            );
            requestMap.remove(key);
            return OnebotMessageListBuilder.builder().addTextMessage(String.format("""
                    入群请求ID：%d
                    已经达到拒绝猫数上限喵，请求已被拒绝。""", userId)).build();
        } else {
            return OnebotMessageListBuilder.builder().addTextMessage(String.format("""
                    入群请求ID：%d
                    拒绝成功喵，目前还需要 %d 猫拒绝喵。""", userId, value.requestRejectNum - rejectNum)).build();
        }
    }

    /**
     * 接受入群请求。
     *
     * @param groupId 群聊ID
     * @param userId  请求者ID
     * @param opId    操作者ID
     */
    @Nullable
    public List<ITeaNekoMessage> accept(long groupId, long userId, long opGroupId, long opId) {
        var key = HashPair.of(groupId, userId);
        var value = requestMap.get(key);
        // 如果不存在请求数据
        if (value == null) {
            return OnebotMessageListBuilder.builder().addTextMessage("该请求不存在喵").build();
        }
        // 如果已经处理过该请求
        if (value.isHandled(opId)) {
            return OnebotMessageListBuilder.builder().addTextMessage("你已经处理过该请求了喵").build();
        }
        int acceptNum = value.accept(opGroupId, opId);

        if (acceptNum >= value.requestAcceptNum) {
            groupAddRequestSender.approve(value.getRequestData().getFlag());
            requestMap.remove(key);
            return OnebotMessageListBuilder.builder().addTextMessage(String.format("""
                    入群请求ID：%d
                    已经达到接受猫数上限喵，请求已被接受。""", userId)).build();
        } else {
            return OnebotMessageListBuilder.builder().addTextMessage(String.format("""
                    入群请求ID：%d
                    接受成功喵，目前还需要 %d 猫接受喵。""", userId, value.requestAcceptNum - acceptNum)).build();
        }
    }


    @Getter
    public class GroupRequestValue {
        /// 最少需要多少猫同意入群请求
        private final int requestAcceptNum;

        /// 最少需要多少猫拒绝入群请求
        private final int requestRejectNum;

        /// 创建时间
        private final long createTime = System.currentTimeMillis();

        /// 请求数据
        private final GroupRequestData requestData;

        /// 请求者的资料
        private final StrangerInfoGetResponseData strangerInfo;

        /// 处理过该请求的集合
        private final Map<Long, Boolean> handledMap = new ConcurrentHashMap<>();

        /// 同意入群数量
        private final AtomicInteger acceptNum = new AtomicInteger(0);

        /// 拒绝入群数量
        private final AtomicInteger rejectNum = new AtomicInteger(0);

        public GroupRequestValue(GroupRequestData requestData,
                                 StrangerInfoGetResponseData strangerInfo,
                                 int requestAcceptNum,
                                 int requestRejectNum) {
            this.requestData = requestData;
            this.strangerInfo = strangerInfo;
            this.requestAcceptNum = requestAcceptNum;
            this.requestRejectNum = requestRejectNum;
        }

        /**
         * 判断该请求是否过期。
         * 超过 48 小时未处理的请求会被视为过期。
         * @return 是否过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > 48 * 60 * 60 * 1000;
        }

        /**
         * 接受入群请求，并返回接受数量。
         *
         * @param opID 操作者ID
         * @return 接受数量
         */
        public int accept(long opGroupId, long opID) {
            // 如果是管理员，则直接返回最大值
            if(groupSeniorServantRule.isAdmin(opGroupId, opID)) {
                return requestAcceptNum;
            }
            // 否则将该请求添加到处理过的集合中
            if(!handledMap.containsKey(opID)) {
                handledMap.put(opID, true);
                return acceptNum.incrementAndGet();
            }
            return acceptNum.get();
        }

        /**
         * 拒绝入群请求，并返回拒绝数量。
         *
         * @return 拒绝数量
         */
        public int reject(long opGroupId, long opID) {
            // 如果是管理员，则直接返回最大值
            if(groupSeniorServantRule.isAdmin(opGroupId, opID)) {
                return requestRejectNum;
            }
            // 否则将该请求添加到处理过的集合中
            if(!handledMap.containsKey(opID)) {
                handledMap.put(opID, false);
                return rejectNum.incrementAndGet();
            }
            return rejectNum.get();
        }

        /**
         * 判断该操作者是否已经处理过该请求。
         * @param opID 操作者ID
         */
        public boolean isHandled(long opID) {
            return handledMap.containsKey(opID);
        }
    }
}
