# 客户端设计思路：
1. 统一客户端、适配器接口
2. 接收： 客户端 → 接收适配器(转化统一的信息事件) → 接收事件 → 监听器 → 命令
3. 发送： 发送适配器 → 发送事件(确保发送的客户端)

信息和事件都是支持重写的。

老监听器可以复用新事件：假设有 父事件 A 和 子事件 B，B 继承 A
1. 事件 A 不会触发监听器 B，但会触发监听器 A；
2. 事件 B 会触发监听器 A 和 B

同理，老指令可以复用新消息，假设有 CommandData<? extends IMessage>：
1. 如果指令里是 CommandData<IMessage>，则可以接收 IMessage 以及 IMessage 的子类事件；
2. 如果是 CommandData<SpecificMessage>，则只能接收 SpecificMessage 事件以及 SpecificMessage 的子类事件。