# 一. Utils 结构介绍

`utils` 提供跨模块工具类和 Spring/Class 扫描辅助能力。

| 区域 | 作用 |
|:---:|---|
| 根目录工具类 | 字符串、日期、随机数、异常、反射字段、方法引用、JSON 描述等工具。 |
| `scanner` | Bean 扫描器和 Class 扫描器实现。 |
| `scanner/interfaces` | 扫描器接口。 |
| `scanner/exception` | 注解与接口不一致异常。 |

# 二. 根目录工具类

| 类 | 作用 |
|---|---|
| `StringUtils` | URL 格式校验。 |
| `ChinaDateUtil` | 中国时区日期时间转换、格式化、当前时间获取和 Cron 下次触发时间计算。 |
| `RandomUtil` | 基于 `ThreadLocalRandom` 的随机整数、布尔值和浮点数生成。 |
| `ExceptionUtils` | 生成异常详情字符串或字符串列表，包含 cause 和 suppressed。 |
| `ObjectFieldUtil` | 通过反射设置字段、解析字符串值、操作 List 字段。 |
| `MethodCallableUtils` | 从 `MethodCallable` 方法引用中提取 `Method` 或指定注解。 |
| `JsonDescriptionUtil` | 根据字段和 `@Description/@Ignore/@Mask` 生成 JSON 描述文本，并缓存结果。 |

# 三. 扫描器 API

| API | 说明 |
|---|---|
| `IBeanScanner.getBeanClass(Object)` | 获取 Spring AOP 代理背后的真实 class。 |
| `IBeanScanner.getBeansOfType(Class<T>)` | 获取指定类型的 Spring Bean。 |
| `IBeanScanner.getBeansWithAnnotation(Class<A>)` | 获取带指定注解的 Bean 和注解实例。 |
| `IBeanScanner.getBeansWithAnnotationAndInterface(annotation, interface)` | 获取同时带注解且实现接口的 Bean；不一致时抛出异常。 |
| `IClassScanner.getClassesWithInterface(Class<T>)` | 扫描自动配置包下实现指定接口的非接口、非抽象 class。 |
| `IClassScanner.getClassesWithAnnotation(Class<A>)` | 扫描带指定注解的 class。 |
| `IClassScanner.getClassesWithAnnotationAndInterface(annotation, interface)` | 扫描同时带注解且实现接口的 class。 |

# 四. 常用工具 API

| API | 说明 |
|---|---|
| `StringUtils.Instance.isValidUrl(url)` | 判断 URL 是否匹配项目内的基础 URL 正则。 |
| `ChinaDateUtil.Instance.convertToDateString(millis)` | 毫秒时间戳转 `yyyy-MM-dd`。 |
| `ChinaDateUtil.Instance.convertToDateTimeString(millis)` | 毫秒时间戳转 `yyyy-MM-dd HH:mm:ss`。 |
| `ChinaDateUtil.Instance.getNextTriggerTime(cron, millis)` | 获取 Cron 表达式在指定时间后的下一次触发时间。 |
| `ExceptionUtils.instance.buildExceptionMessage(throwable)` | 构造完整异常文本。 |
| `ObjectFieldUtil.instance.setFieldValue(objectMapper, obj, field, value)` | 将字符串解析为字段类型并写入对象。 |
| `ObjectFieldUtil.instance.addToListField(...)` | 向 List 字段追加元素。 |
| `ObjectFieldUtil.instance.removeFromListField(...)` | 移除 List 字段指定索引。 |
| `MethodCallableUtils.instance.getAnnotation(callable, Annotation.class)` | 从方法引用对应方法中获取注解。 |
| `JsonDescriptionUtil.toJsonDescription(Class<?>)` | 生成适合展示或提示的 JSON 字段说明。 |

# 五. 使用示例

```java
boolean valid = StringUtils.Instance.isValidUrl("https://example.com/a");

String now = ChinaDateUtil.Instance.getNowDateTimeString();

String message = ExceptionUtils.instance.buildExceptionMessage(throwable);
```

```java
Map<String, Pair<TaskStage, ITaskStage>> stages =
        beanScanner.getBeansWithAnnotationAndInterface(TaskStage.class, ITaskStage.class);
```

# 六. 注意事项

| 场景 | 说明 |
|---|---|
| 包名拼写 | `scanner/inerfaces` 目录名按当前代码拼写保留。 |
| Class 扫描范围 | `ClassScanner` 使用 Spring Boot `AutoConfigurationPackages`，扫描范围取决于应用启动包配置。 |
| 反射字段修改 | `ObjectFieldUtil` 会穿透父类字段，但字段名错误会抛出 `NoSuchFieldException`。 |
| JSON 描述 | `JsonDescriptionUtil` 会缓存 class 描述结果，字段注解变化需要重启或清缓存后才能反映。 |
