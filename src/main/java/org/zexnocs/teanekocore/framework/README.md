# 一. Framework 结构介绍

`framework` 放置跨模块使用的基础抽象和小型数据结构，不依赖具体业务模块。

| 子模块 | 作用 |
|:---:|---|
| `bimap` | 双向映射接口和线程安全实现。 |
| `description` | 描述、忽略、掩码注解，用于反射描述或安全展示。 |
| `function` | 可序列化 callable 和无返回值 callable。 |
| `lifescycle` | 生命周期与暂停控制接口。 |
| `pair` | pair 数据结构。 |
| `state` | 状态机接口和带锁实现。 |

# 二. 主要 API

## 1. bimap

| API | 说明 |
|---|---|
| `IBimap<K, V>` | 双向映射接口，支持 key->value 和 value->key 查询。 |
| `LockBiMap<K, V>` | 使用 `ReentrantReadWriteLock` 的线程安全双向映射。 |
| `put(K, V)` | 写入映射；如果 key 或 value 已存在，会移除旧映射并返回被替换的 pair。 |
| `removeByKey(K)` / `removeByValue(V)` | 双向删除。 |
| `entrySet()` | 返回只读快照，避免外部破坏双向关系。 |

## 2. description

| 注解 | 说明 |
|---|---|
| `@Description("...")` | 为类、方法或字段提供描述。`JsonDescriptionUtil` 会读取它。 |
| `@Ignore` | 标记不参与描述生成。 |
| `@Mask("****")` | 标记敏感字段，展示时使用掩码。 |

## 3. function

| API | 说明 |
|---|---|
| `MethodCallable<T>` | 继承 `Callable<T>` 和 `Serializable`，用于从 lambda 或方法引用中提取方法注解。 |
| `VoidCallable` | `void call() throws Exception`，用于数据库事务任务和缓存任务。 |

## 4. lifescycle

| API | 说明 |
|---|---|
| `ILivable.isAlive()` | 生命周期判断。返回 `false` 后对象可被外部移除。 |
| `IPausable.pause()` / `resume()` / `isPaused()` | 暂停控制接口。 |
| `LambdaPausable` | 只要求实现 `isPaused()` 的函数式接口，默认不支持主动 `pause/resume`。 |

## 5. pair

| API | 说明 |
|---|---|
| `Pair<F, S>` | 基础 pair 接口。 |
| `HashPair<F, S>` | record 实现，按 first/second 参与 equals 和 hashCode。 |
| `IndependentPair<F, S>` | 普通不可变 pair，不额外定义 equals/hashCode。 |
| `UnorderedPair<T>` | 无序 pair，`(a, b)` 与 `(b, a)` 相等且 hashCode 相同。 |

## 6. state

| API | 说明 |
|---|---|
| `IState` | 状态接口，提供 `onEnter()`、`onExit()`、`update(long)` 默认方法。 |
| `IStateMachine<T extends IState>` | 状态机接口，支持读取、判断和切换状态。 |
| `LockStateMachine<T>` | 使用 `ReentrantLock` 的线程安全状态机。 |
| `switchState(newState)` | 先替换状态，再调用旧状态 `onExit()` 和新状态 `onEnter()`。 |
| `switchStateUnderExpected(expected, newState)` | 当前状态满足 expected 时才切换。 |

# 三. 使用示例

```java
LockBiMap<String, Integer> map = new LockBiMap<>();
map.put("one", 1);
Integer value = map.getValue("one");
String key = map.getKey(1);
```

```java
public class OpenState implements IState {
    @Override
    public void onEnter() {
        // init
    }
}

LockStateMachine<IState> machine = new LockStateMachine<>(new OpenState());
machine.switchState(new ClosedState());
```

# 四. 注意事项

| 场景 | 说明 |
|---|---|
| 包名 | `lifescycle` 目录名按当前代码拼写保留。 |
| `LockBiMap.put` | 新 key 或 value 与已有映射冲突时，会维护双向唯一关系。 |
| `LockStateMachine` | 状态替换在锁内完成，`onExit/onEnter` 在锁外调用，避免钩子阻塞状态锁。 |
