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
| `FileConfigType.YAML` | `.yml` 文件。 |
| `IFileConfigService.get(Class<T>)` | 获取最后一次成功加载的配置实例。 |
| `IFileConfigService.requireCurrent(Class<T>)` | 读取最近一次加载成功的数据，最新加载失败时明确拒绝。 |
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

# 六. 兼容读取与严格读取

`IFileConfigService.get(type)` 返回最后一次成功加载的数据。后续重载失败时保留原有效对象，兼容已有业务的缓存读法；从未加载成功则抛出 FileConfigDataNotFoundException。

`requireCurrent(type)` 仅接受最近一次已完成加载成功的数据。解析失败、解析器缺失、空解析结果或根目录不可用时明确失败，不沿用残留旧值冒充当前成功。数据与该类型的加载状态作为同一缓存记录原子发布；不提供跨多个配置类型的事务快照。

成功重载或写入会发布新的有效记录。失败记录保留最后有效值供 get 使用，只保存状态，不缓存原始异常或文件内容。严格读取的错误消息不包含配置值或原始解析异常链。根目录读取失败需要通过后续成功重载恢复；原始加载诊断仍由既有文件服务日志负责。

自定义 IFileConfigService 实现若未提供严格状态支持，requireCurrent 明确拒绝，不能静默退回 get。新 Agent 使用严格读取主配置及模型配置，避免重载失败后继续执行；其他现有调用的读取语义保持兼容。
