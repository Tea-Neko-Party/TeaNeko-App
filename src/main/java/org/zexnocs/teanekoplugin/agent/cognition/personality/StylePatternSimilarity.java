package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 表达模式的保守本地近义比较，不调用另一个模型，也不跨字段、场景或否定方向合并。
 * <br>2026/11/06：规范常见同义措辞后比较文字特征；不确定时保留独立候选。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class StylePatternSimilarity {
    /** 禁止实例化纯比较工具。 */
    private StylePatternSimilarity() { }

    /**
     * 比较同字段和场景的措辞，避免把相反频率、否定或不同用途合并。
     * @param left 既有表达
     * @param right 新表达
     * @return 有明确近义依据时为真
     */
    public static boolean equivalent(String left, String right) {
        var a = normalized(left);
        var b = normalized(right);
        if (a.equals(b)) {
            return true;
        }
        if (!qualifiers(a).equals(qualifiers(b))) {
            return false;
        }
        if (repeatedGreeting(a) && repeatedGreeting(b)) {
            return true;
        }
        var first = terms(a);
        var second = terms(b);
        if (first.size() < 3 || second.size() < 3) {
            return false;
        }
        var common = new HashSet<>(first);
        common.retainAll(second);
        return (2.0 * common.size()) / (first.size() + second.size()) >= 0.78;
    }

    /**
     * 统一同义表达和无语义标点，保留否定、频率和实际模式内容。
     * @param value 模式原文
     * @return 稳定比较文本，也用于新记录标识
     */
    public static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace("打招呼", "问候").replace("招呼", "问候")
                .replace("单个词语", "单词").replace("单个词", "单词").replace("单一词语", "单词")
                .replace("反复", "重复").replace("多次", "重复")
                .replace("经常", "常").replace("常常", "常").replace("常用", "常使用")
                .replace("习惯于", "常").replace("习惯", "常")
                .replace("喜欢使用", "常使用").replace("倾向于使用", "常使用").replace("采用", "使用")
                .replace("用户", "").replace("进行", "").replace("来问候", "问候").replace("来重复", "重复")
                .replace("的方式", "方式").replace("的形式", "形式")
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    /**
     * 对已确认的同义模式生成稳定语义键，使到期后的采用标记不依赖词序。
     * @param value 模式描述
     * @return 不改变原展示文本的规范键
     */
    public static String key(String value) {
        var text = normalized(value);
        return repeatedGreeting(text) ? "重复单词问候" : text;
    }

    /**
     * 否定和低频限定词必须一致，不能仅因大部分词相同合并相反结论。
     * @param value 已规范文本
     * @return 语义限定集合
     */
    private static Set<String> qualifiers(String value) {
        var result = new HashSet<String>();
        for (var term : Set.of("不", "未", "无", "避免", "很少", "偶尔", "仅", "只")) {
            if (value.contains(term)) {
                result.add(term);
            }
        }
        return result;
    }

    /**
     * 使用有界 Unicode 双字特征，不对汉字做不可靠的空格分词。
     * @param value 已规范文本
     * @return 去重特征
     */
    private static Set<String> terms(String value) {
        int[] points = value.codePoints().toArray();
        var result = new HashSet<String>();
        for (int i = 0; i + 1 < points.length; i++) {
            result.add(new String(points, i, 2));
        }
        return result;
    }

    /**
     * 识别词序不同但完整语义均为单词重复问候的表达，不吞掉其他限定内容。
     * @param text 已规范文本
     * @return 是否仅描述这一种模式
     */
    private static boolean repeatedGreeting(String text) {
        return text.contains("单词") && text.contains("重复") && text.contains("问候")
                && text.replace("单词", "").replace("重复", "").replace("问候", "")
                .replace("常", "").replace("使用", "").replace("用", "").replace("以", "")
                .replace("方式", "").replace("形式", "").isEmpty();
    }
}
