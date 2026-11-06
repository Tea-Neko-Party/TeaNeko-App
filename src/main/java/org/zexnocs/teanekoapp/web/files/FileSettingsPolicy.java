package org.zexnocs.teanekoapp.web.files;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 按声明的 JSON 指针投影和局部修改原树，不返回未知节点或把脱敏占位符写回。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：显式开放字段复用原解析器注解说明，保持秘密保护。
 *
 * <br>2026/11/06：向文件表单投影统一编辑器元数据。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.4-l
 */
final class FileSettingsPolicy {
    /** 禁止实例化字段策略工具。 */
    private FileSettingsPolicy() {}

    /**
     * @param adapter 原声明
     */
    static void validateSchema(IFileSettingsAdapter adapter) {
        if (adapter.fields().isEmpty() || adapter.fields().size() > 100
                || !Set.of("NEXT_USE", "RESTART").contains(adapter.application())) {
            throw new IllegalArgumentException("文件管理声明无效。");
        }
        for (var rule : adapter.fields()) {
            if (!rule.path().startsWith("/") || rule.path().length() > 256
                    || !Set.of("BOOLEAN", "NUMBER", "TEXT", "JSON").contains(rule.kind())) {
                throw new IllegalArgumentException("文件字段声明无效。");
            }
            if (adapter.fields().stream().filter(other -> prefix(rule.path(), other.path())).count() != 1) {
                throw new IllegalArgumentException("文件字段不能重复或包含另一开放字段。");
            }
        }
        for (var collection : adapter.collections()) {
            if (!collection.path().startsWith("/") || collection.path().contains("*")
                    || collection.maximum() < 1 || collection.maximum() > 100) {
                throw new IllegalArgumentException("文件集合声明无效。");
            }
        }
    }

    /**
     * @param tree 原树
     * @param adapter 原字段声明
     * @param exists 文件是否已存在
     * @param schema 原文件解析器提供的类型元数据，不支持时为空
     * @return 仅明确字段的脱敏视图
     */
    static List<FileSettings.Field> fields(JsonNode tree, IFileSettingsAdapter adapter, boolean exists,
            org.zexnocs.teanekocore.framework.config.@org.jspecify.annotations.Nullable ConfigSchema schema) {
        var fields = new ArrayList<FileSettings.Field>();
        for (var rule : adapter.fields()) {
            for (String path : expand(tree, rule.path())) {
                var node = tree.at(path);
                boolean configured = !node.isMissingNode() && !node.isNull()
                        && (node.isString() ? !node.asString().isBlank() : !node.isContainer() || !node.isEmpty());
                // 2026/11/04：说明来源为类型注解，秘密字段仍只显示声明与状态。
                String explanation = schema == null ? rule.description() : schema.findPointer(path)
                        .map(org.zexnocs.teanekocore.framework.config.ConfigSchema.Property::description)
                        .filter(value -> !value.equals("尚未提供说明"))
                        .map(value -> value.equals(rule.description()) ? value : value + "\n" + rule.description())
                        .orElse(rule.description());
                fields.add(new FileSettings.Field(path, rule.label(), explanation, rule.kind(), rule.secret(),
                        configured, node.isMissingNode() ? "未声明，使用类型默认值" : exists ? "文件原值或环境模板" : "未保存模板",
                        rule.secret() || node.isMissingNode() ? null : node.deepCopy(),
                        rule.secret() || schema == null ? "AUTO" : schema.findPointer(path)
                                .map(org.zexnocs.teanekocore.framework.config.ConfigSchema.Property::editor)
                                .orElse("AUTO")));
            }
        }
        return List.copyOf(fields);
    }

    /**
     * @param original 原文档树
     * @param adapter 当前允许规则
     * @param edits 明确修改
     * @return 保留未修改节点与环境模板的新树
     */
    static JsonNode edit(JsonNode original, IFileSettingsAdapter adapter, List<FileSettings.Edit> edits) {
        var tree = original.deepCopy();
        if (!(tree instanceof ObjectNode)) {
            throw new IllegalArgumentException("文件根节点必须是对象。");
        }
        for (var collection : adapter.collections()) {
            long structural = edits.stream().filter(edit -> edit.path().equals(collection.path())
                    && edit.action().equals("APPEND") || element(collection.path(), edit.path())
                    && edit.action().equals("REMOVE")).count();
            long children = edits.stream().filter(edit -> edit.path().equals(collection.path())
                    || edit.path().startsWith(collection.path() + "/")).count();
            if (structural > 0 && children != 1) {
                throw new IllegalArgumentException("集合增删必须单独保存，避免数组索引移动后修改错误条目。");
            }
        }
        for (var edit : edits) {
            var collection = adapter.collections().stream().filter(value -> value.path().equals(edit.path())
                    || element(value.path(), edit.path())).findFirst().orElse(null);
            if (collection != null && edit.action().equals("APPEND") && edit.path().equals(collection.path())) {
                var array = tree.at(collection.path());
                if (array.isMissingNode()) {
                    set(tree, collection.path(), new ArrayNode(JsonNodeFactory.instance), false);
                    array = tree.at(collection.path());
                }
                if (!(array instanceof ArrayNode values) || values.size() >= collection.maximum()
                        || edit.value() == null || !edit.value().isObject()) {
                    throw new IllegalArgumentException("新增配置条目或集合容量无效。");
                }
                validateNew(edit.value(), collection.path() + "/" + values.size(), adapter.fields());
                values.add(edit.value().deepCopy());
                continue;
            }
            if (collection != null && edit.action().equals("REMOVE") && element(collection.path(), edit.path())) {
                set(tree, edit.path(), NullNode.getInstance(), true);
                continue;
            }
            var rule = adapter.fields().stream().filter(value -> matches(value.path(), edit.path())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("请求包含未开放的文件字段。"));
            if (rule.secret()) {
                if (!Set.of("REPLACE_SECRET", "CLEAR_SECRET").contains(edit.action())) {
                    throw new IllegalArgumentException("受保护字段只能显式替换或清除。");
                }
            } else if (!Set.of("SET", "REMOVE").contains(edit.action())) {
                throw new IllegalArgumentException("文件字段动作无效。");
            }
            boolean remove = edit.action().equals("REMOVE") || edit.action().equals("CLEAR_SECRET");
            if (!remove && (edit.value() == null || edit.value().isNull())) {
                throw new IllegalArgumentException("设置字段必须提供明确的新值。");
            }
            set(tree, edit.path(), remove ? NullNode.getInstance() : edit.value().deepCopy(), remove);
        }
        return tree;
    }

    /**
     * @param node 新条目
     * @param path 当前前缀
     * @param rules 明确规则
     */
    private static void validateNew(JsonNode node, String path, List<FileSettings.Rule> rules) {
        if (rules.stream().anyMatch(rule -> matches(rule.path(), path))) {
            return;
        }
        String pattern = path.replaceAll("/(?:0|[1-9][0-9]{0,2})(?=/|$)", "/*");
        if (!node.isObject() || rules.stream().noneMatch(rule -> rule.path().startsWith(pattern + "/"))) {
            throw new IllegalArgumentException("新增条目包含未声明字段。");
        }
        for (String name : node.propertyNames()) {
            validateNew(node.get(name), path + "/" + name.replace("~", "~0").replace("/", "~1"), rules);
        }
    }

    /**
     * @param tree 原树
     * @param pattern 原模板路径
     * @return 当前实际路径，缺少普通字段仍返回该字段
     */
    private static List<String> expand(JsonNode tree, String pattern) {
        int wildcard = pattern.indexOf("/*/");
        if (wildcard < 0) {
            return List.of(pattern);
        }
        String prefix = pattern.substring(0, wildcard);
        var array = tree.at(prefix);
        if (array.isMissingNode()) {
            return List.of();
        }
        if (!array.isArray() || array.size() > 100) {
            throw new IllegalArgumentException("文件集合不是有界数组。");
        }
        var result = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            result.add(prefix + "/" + i + pattern.substring(wildcard + 2));
        }
        return result;
    }

    /**
     * @param pattern 原声明
     * @param path 实际路径
     * @return 精确匹配或一层数字索引匹配
     */
    private static boolean matches(String pattern, String path) {
        return pattern.split("/", -1).length == path.split("/", -1).length && prefix(pattern, path);
    }

    /**
     * @param first 原规则前缀
     * @param second 被比较路径
     * @return 是否可能形成祖先或重复路径
     */
    private static boolean prefix(String first, String second) {
        var left = first.split("/", -1);
        var right = second.split("/", -1);
        if (left.length > right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!left[i].equals(right[i]) && !(left[i].equals("*")
                    && (right[i].equals("*") || right[i].matches("0|[1-9][0-9]{0,2}")))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param collection 集合前缀
     * @param path 实际路径
     * @return 是否为集合中一个数字索引
     */
    private static boolean element(String collection, String path) {
        return path.startsWith(collection + "/") && path.substring(collection.length() + 1).matches("0|[1-9][0-9]{0,2}");
    }

    /**
     * @param tree 原可写树
     * @param path 已授权指针
     * @param value 新节点
     * @param remove 是否删除
     */
    private static void set(JsonNode tree, String path, JsonNode value, boolean remove) {
        String[] tokens = path.substring(1).split("/", -1);
        JsonNode parent = tree;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].replace("~1", "/").replace("~0", "~");
            boolean last = i == tokens.length - 1;
            if (parent instanceof ObjectNode object) {
                if (last) {
                    if (remove) {
                        object.remove(token);
                    } else {
                        object.set(token, value);
                    }
                    return;
                }
                if (!object.has(token)) {
                    if (remove) {
                        return;
                    }
                    object.set(token, new ObjectNode(JsonNodeFactory.instance));
                }
                parent = object.get(token);
            } else if (parent instanceof ArrayNode array && token.matches("0|[1-9][0-9]{0,2}")) {
                int index = Integer.parseInt(token);
                if (index >= array.size()) {
                    throw new IllegalArgumentException("配置数组索引已经无效。");
                }
                if (last) {
                    if (remove) {
                        array.remove(index);
                    } else {
                        array.set(index, value);
                    }
                    return;
                }
                parent = array.get(index);
            } else {
                throw new IllegalArgumentException("配置字段的父节点类型无效。");
            }
        }
    }
}
