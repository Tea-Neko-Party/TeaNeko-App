# 一. File Config 结构介绍

`file_config` 用于把配置数据类映射到本地配置文件，并支持 JSON/YAML 解析、模板复制、默认构造和热重载。

| 区域 | 作用 |
|:---:|---|
| `api` | 文件配置数据接口、配置注解和文件类型枚举。 |
| `interfaces` | 文件配置服务接口、文件类型解析器接口和解析器注解。 |
| `parser` | 内置 JSON 和 YAML 解析器。 |
| `exception` | 配置数据缺失异常。 |
| `FileConfigService` | 配置加载、写入、缓存和 reload。 |
| `FileTypeScanner` | 扫描 `@FileTypeParser` 并按 `FileConfigType` 注册解析器。 |

# 二. 配置加载规则

配置文件根目录是项目或 jar 同级的：

```java
FileConfigService.ROOT_PATH // config
```

模板目录是 classpath 下的：

```java
FileConfigService.TEMPLATE_PATH // templates/config
```

加载顺序：

```markdown
1. 扫描所有带 @FileConfig 且实现 IFileConfigData 的类。
2. 根据 @FileConfig.path、value 和 FileTypeParser.getSuffix() 构造 config 文件路径。
3. 如果 config 文件存在，直接读取并反序列化。
4. 如果 config 文件不存在，尝试从 classpath:templates/config 下复制模板。
5. 如果模板也不存在，使用无参构造器创建默认配置，并写入 config 目录。
6. 成功加载后放入内存缓存。
```

# 三. 主要 API

| API | 说明 |
|---|---|
| `@FileConfig(value, path, type)` | 标记配置数据类。`value` 是文件名，`path` 是相对 `config/` 的目录，`type` 默认 `JSON`。 |
| `IFileConfigData` | 文件配置数据标记接口，继承 `Serializable`。实现类需要无参构造器。 |
| `FileConfigType.JSON` | `.json` 文件，内置解析器支持带 `#` 注释的 JSON。 |
| `FileConfigType.YAML` | `.yaml` 文件。 |
| `IFileConfigService.get(Class<T>)` | 获取已加载的配置实例。 |
| `IFileConfigService.write(IFileConfigData)` | 写回配置文件并刷新缓存。 |
| `IFileConfigService.reload()` | 重新扫描并加载配置。 |
| `@FileTypeParser(FileConfigType.X)` | 标记自定义文件类型解析器。 |
| `IFileTypeParser.fromFileToData(...)` | 从输入流读取配置对象。 |
| `IFileTypeParser.fromDataToWrite(...)` | 将配置对象写入文件。 |
| `IFileTypeParser.getSuffix()` | 返回文件后缀，不包含 `.`。 |

# 四. 使用示例

```java
@FileConfig(value = "bot", path = "teaneko", type = FileConfigType.JSON)
public class BotConfig implements IFileConfigData {
    public String token = "";
    public boolean enabled = true;
}

BotConfig config = fileConfigService.get(BotConfig.class);
config.enabled = false;
fileConfigService.write(config);
```

对应文件路径：

```markdown
config/teaneko/bot.json
```

如果存在模板，模板路径为：

```markdown
classpath:templates/config/teaneko/bot.json
```

# 五. 注意事项

| 场景 | 说明 |
|---|---|
| 构造器中读取配置 | 如果在 Bean 构造器里读取配置，可能早于 reload 完成。建议依赖 `IReloadService` 或在 `ApplicationReadyEvent` 后读取。 |
| 自定义解析器 | 同一个 `FileConfigType` 只应有一个解析器；重复注册时保留先扫描到的解析器并记录 warn。 |
| 写入 | `write` 会创建父目录、写文件并更新缓存。 |
| 初始化顺序 | `FileTypeScanner` 的优先级高于 `FileConfigService`，保证解析器先注册。 |
