package org.zexnocs.teanekoclient.onebot.data.response.params._private;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 陌生人信息获取响应子数据
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StrangerInfoGetResponseData {

    /**
     * 用户唯一标识
     */
    @JsonProperty("uid")
    private String uid;

    /**
     * 用户 QQ 号
     */
    @JsonProperty("uin")
    private long uin;

    /**
     * 用户昵称
     */
    @JsonProperty("nick")
    private String nick;

    /**
     * 备注信息
     */
    @JsonProperty("remark")
    private String remark;

    /**
     * 星座
     */
    @JsonProperty("constellation")
    private int constellation;

    /**
     * 生肖
     */
    @JsonProperty("shengXiao")
    private int shengXiao;

    /**
     * 血型
     */
    @JsonProperty("kBloodType")
    private int kBloodType;

    /**
     * 家乡
     */
    @JsonProperty("homeTown")
    private String homeTown;

    /**
     * 交友职业
     */
    @JsonProperty("makeFriendCareer")
    private int makeFriendCareer;

    /**
     * 位置/个性签名
     */
    @JsonProperty("pos")
    private String pos;

    /**
     * 大学
     */
    @JsonProperty("college")
    private String college;

    /**
     * 国家
     */
    @JsonProperty("country")
    private String country;

    /**
     * 省份
     */
    @JsonProperty("province")
    private String province;

    /**
     * 城市
     */
    @JsonProperty("city")
    private String city;

    /**
     * 邮政编码
     */
    @JsonProperty("postCode")
    private String postCode;

    /**
     * 地址
     */
    @JsonProperty("address")
    private String address;

    /**
     * 注册时间
     */
    @JsonProperty("regTime")
    private long regTime;

    /**
     * 兴趣
     */
    @JsonProperty("interest")
    private String interest;

    /**
     * 标签列表
     */
    @JsonProperty("labels")
    private List<String> labels;

    /**
     * QQ 等级
     */
    @JsonProperty("qqLevel")
    private int qqLevel;

    /**
     * QID
     */
    @JsonProperty("qid")
    private String qid;

    /**
     * 长昵称
     */
    @JsonProperty("longNick")
    private String longNick;

    /**
     * 出生年份
     */
    @JsonProperty("birthday_year")
    private int birthdayYear;

    /**
     * 出生月份
     */
    @JsonProperty("birthday_month")
    private int birthdayMonth;

    /**
     * 出生日期
     */
    @JsonProperty("birthday_day")
    private int birthdayDay;

    /**
     * 年龄
     */
    @JsonProperty("age")
    private int age;

    /**
     * 性别
     */
    @JsonProperty("sex")
    private String sex;

    /**
     * 邮箱
     */
    @JsonProperty("eMail")
    private String email;

    /**
     * 手机号
     */
    @JsonProperty("phoneNum")
    private String phoneNum;

    /**
     * 分类ID
     */
    @JsonProperty("categoryId")
    private int categoryId;

    /**
     * 富文本时间
     */
    @JsonProperty("richTime")
    private long richTime;

    /**
     * 富文本缓冲区
     */
    @JsonProperty("richBuffer")
    private Map<String, Object> richBuffer;

    /**
     * 置顶时间
     */
    @JsonProperty("topTime")
    private String topTime;

    /**
     * 是否被屏蔽
     */
    @JsonProperty("isBlock")
    private boolean isBlock;

    /**
     * 是否消息免打扰
     */
    @JsonProperty("isMsgDisturb")
    private boolean isMsgDisturb;

    /**
     * 是否开启特别关心
     */
    @JsonProperty("isSpecialCareOpen")
    private boolean isSpecialCareOpen;

    /**
     * 是否特别关心区域
     */
    @JsonProperty("isSpecialCareZone")
    private boolean isSpecialCareZone;

    /**
     * 铃声ID
     */
    @JsonProperty("ringId")
    private String ringId;

    /**
     * 是否被对方屏蔽
     */
    @JsonProperty("isBlocked")
    private boolean isBlocked;

    /**
     * 推荐图片标志
     */
    @JsonProperty("recommendImgFlag")
    private int recommendImgFlag;

    /**
     * 禁用表情快捷键
     */
    @JsonProperty("disableEmojiShortCuts")
    private int disableEmojiShortCuts;

    /**
     * 企点主标志
     */
    @JsonProperty("qidianMasterFlag")
    private int qidianMasterFlag;

    /**
     * 企点成员标志
     */
    @JsonProperty("qidianCrewFlag")
    private int qidianCrewFlag;

    /**
     * 企点成员标志2
     */
    @JsonProperty("qidianCrewFlag2")
    private int qidianCrewFlag2;

    /**
     * 是否隐藏QQ等级
     */
    @JsonProperty("isHideQQLevel")
    private int isHideQQLevel;

    /**
     * 是否隐藏特权图标
     */
    @JsonProperty("isHidePrivilegeIcon")
    private int isHidePrivilegeIcon;

    /**
     * 在线状态
     */
    @JsonProperty("status")
    private int status;

    /**
     * 扩展状态
     */
    @JsonProperty("extStatus")
    private int extStatus;

    /**
     * 电池状态
     */
    @JsonProperty("batteryStatus")
    private int batteryStatus;

    /**
     * 终端类型
     */
    @JsonProperty("termType")
    private int termType;

    /**
     * 网络类型
     */
    @JsonProperty("netType")
    private int netType;

    /**
     * 图标类型
     */
    @JsonProperty("iconType")
    private int iconType;

    /**
     * 自定义状态
     */
    @JsonProperty("customStatus")
    private Object customStatus;

    /**
     * 设置时间
     */
    @JsonProperty("setTime")
    private String setTime;

    /**
     * 特殊标志
     */
    @JsonProperty("specialFlag")
    private int specialFlag;

    /**
     * ABI标志
     */
    @JsonProperty("abiFlag")
    private int abiFlag;

    /**
     * 网络类型枚举
     */
    @JsonProperty("eNetworkType")
    private int eNetworkType;

    /**
     * 显示名称
     */
    @JsonProperty("showName")
    private String showName;

    /**
     * 终端描述
     */
    @JsonProperty("termDesc")
    private String termDesc;

    /**
     * 音乐信息
     */
    @JsonProperty("musicInfo")
    private Map<String, Object> musicInfo;

    /**
     * 扩展在线业务信息
     */
    @JsonProperty("extOnlineBusinessInfo")
    private Map<String, Object> extOnlineBusinessInfo;

    /**
     * 扩展缓冲区
     */
    @JsonProperty("extBuffer")
    private Map<String, Object> extBuffer;

    /**
     * 用户ID（兼容字段）
     */
    @JsonProperty("user_id")
    private long userId;

    /**
     * 昵称（兼容字段）
     */
    @JsonProperty("nickname")
    private String nickname;

    /**
     * 长昵称（兼容字段）
     */
    @JsonProperty("long_nick")
    private String longNickCompatible;

    /**
     * 注册时间（兼容字段）
     */
    @JsonProperty("reg_time")
    private long regTimeCompatible;

    /**
     * 是否是VIP（兼容字段）
     */
    @JsonProperty("is_vip")
    private boolean isVip;

    /**
     * 是否是年费VIP（兼容字段）
     */
    @JsonProperty("is_years_vip")
    private boolean isYearsVip;

    /**
     * VIP等级（兼容字段）
     */
    @JsonProperty("vip_level")
    private int vipLevel;

    /**
     * 登录天数（兼容字段）
     */
    @JsonProperty("login_days")
    private int loginDays;
}
