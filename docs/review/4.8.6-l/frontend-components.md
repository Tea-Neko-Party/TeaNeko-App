# v4.8.6-l 前端架构与组件优化契约

审查日期：2026/11/06。首发基线为单实例、小规模受控用户。本文评价当前工作区；共享记忆计划在审查开始时进行中，收尾时 S01–S08 已完成并归档。计划中的实施验证与本轮独立审查证据分别记录，真实部署验收仍需单独执行。本轮没有修改产品代码。

结论：Vue、Element Plus、构建期页面声明与现有受控请求机制适合本项目的首发规模，无需迁移前端框架。生产前应补齐写操作回执校验和动态字段可访问名称；手写页签也需要统一行为。模块化应沿现有页面宿主和编辑器逐步拆分，避免另建一套权限、请求或状态框架。

## 现状与职责

| 层次 | 当前职责与证据 | 评价 |
| --- | --- | --- |
| 应用外壳 | [App.vue](../../../frontend/src/app/App.vue) 管认证、布局与会话销毁，已避免旧会话迟到结果重新挂载 | 边界基本合理；不应继续塞入业务分支 |
| 页面声明与宿主 | [page-registry.ts](../../../frontend/src/app/pages/page-registry.ts) 统一导航、标题、可见条件和异步加载；[ConsolePageHost.vue](../../../frontend/src/app/pages/ConsolePageHost.vue) 管保留策略、离开守卫及导航意图 | 已支持增加页面声明扩展；导航可见性不是服务端授权 |
| 业务页面 | 聊天、工作区、配置、运行管理各自拥有数据和操作状态 | 有隔离，但大页面同时承担 DTO、请求、状态、恢复和显示，改动影响面偏大 |
| 共享编辑与展示 | `ValueEditor`、`FileFieldEditor`、`ModelNamePicker`、`RefreshButton`、`DetailPanel` 已复用 | 保留这些基础；重点补齐关联标签、描述与错误的统一合同 |
| 请求与回执 | [api.ts](../../../frontend/src/shared/api/client.ts) 统一同源、CSRF 和 JSON 错误；[management-receipts.ts](../../../frontend/src/shared/api/management-receipts.ts) 已校验部分管理回执 | 运行时回执保护尚未覆盖全部有副作用入口，见 F01 |
| 生命周期与目录 | `usePageDraft`、`useActivePolling` 与 `useAgentLibrary` 管草稿、可见轮询、目录遍历和取消 | 已有合适复用点；应补查询规模和等待期限，避免每个页面重写 |

`WebChatView.vue` 约 669 行、`AgentWorkspaceView.vue` 约 584 行、`ChatSettingsView.vue` 约 519 行。行数本身不构成缺陷；风险来自同一文件同时管理多类请求、DTO、业务恢复与视图，F01 就暴露了不同页面恢复规则漂移。首发不要求全量迁目录或引入路由／全局状态库。

## 已确认的问题

| 编号／优先级 | 故障、触发与影响 | 失效原理 | 建议及验收 |
| --- | --- | --- | --- |
| **V486-F01／P1** | 聊天发送或定时器修改返回 HTTP 200 的 `{}`／其他操作的回执时，原请求立即消失；聊天正文、附图、引用同时清空。结果不明时失去按原编号恢复的状态 | [WebChatView.receive:391](../../../frontend/src/features/chat/WebChatView.vue#L391)、[RuntimeView.receive:122](../../../frontend/src/features/runtime/RuntimeView.vue#L122) 信任 TypeScript 泛型，未核对 `operationId`、状态或目标就清 `pending`。JSON 解析成功不代表业务确认 | 为各端点定义运行时 decoder，确认后才释放原请求。无效回执进入“结果未知”，保留原编号、参数与草稿，不自动新建编号重发。两页各验证空对象、错编号、错目标／字段类型和有效结果；确认后刷新失败不得降回“未提交” |
| **V486-F02／P2** | 打开普通组件参数，浏览器可访问性树呈现无名开关、无名数值框和无名文本框，辅助技术缺少可靠的字段名称 | [AgentAssemblyEditor:167](../../../frontend/src/features/agent/AgentAssemblyEditor.vue#L167) 的可见 `label` 与后面的 `ElSwitch`、`ElInputNumber`、`ElInput` 没有 `for/id` 或 `aria-labelledby` 关联；一般分支也未传 `aria-label` | 由字段容器生成实例唯一 ID，给实际可聚焦控件传名称、描述与错误关联；覆盖布尔、数字、字符串、密钥及编辑器。浏览器按字段名取得控件，并用读屏验证状态和错误。对应适用的 WCAG 2.2 1.3.1、3.3.2、4.1.2 |
| **V486-F03／P2** | 运行管理页签宣称 `tablist/tab`，但方向键不移动焦点；没有 `tabpanel` 和控件关联。相似手写结构散布在聊天配置及 Agent 工作区 | [RuntimeView:172](../../../frontend/src/features/runtime/RuntimeView.vue#L172)、[AgentWorkspaceView:373](../../../frontend/src/features/agent/AgentWorkspaceView.vue#L373)、[ChatSettingsView:415](../../../frontend/src/features/settings/chat/ChatSettingsView.vue#L415) 只设置选中属性，未实现完整页签交互 | 优先复用已有 `ElTabs`，或封装一套共同的手动激活页签；补焦点移动、单一 Tab 停靠点、面板关联和离开保护。浏览器验证方向键、Home/End、Enter/Space、禁用项和返回焦点。这里是页签模式缺口，不能表述成所有功能均无法用键盘操作 |

F01 是原 V484-W01 同类风险在**另外两个页面**的覆盖缺口；不重新打开已完成回执校验的 Agent 工作区和指令台。部署代理异常、版本不一致或后端回归可能返回可解析但不匹配的正文；前端不能把这些情况当作提交确认。本轮没有证据表明真实部署已经发送重复消息。

F02 的字段分支早已存在；4.8.6-l 本批已完成的依赖名称显示改动不是其根因。修正时只调整实际相关组件，保留已完成的共享记忆功能。

F03 按 [WAI-ARIA APG 页签模式](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/)评价方向键焦点移动及页签／面板关系；单独缺少方向键不能直接判定违反 WCAG 的全部键盘可操作要求。当前普通 Tab 与 Enter 路径仍可能可用，完整关键流程须另行验收。

## 设计与容量风险

| 风险 | 当前证据 | 首发及后续边界 |
| --- | --- | --- |
| 网络悬挂缺少统一有界等待 | [api.ts:51](../../../frontend/src/shared/api/client.ts#L51) 使用 `fetch`，未定义产品级请求期限；部分读有 AbortController，写操作通常依赖请求自行结束 | 首发对读取和变更分别设可配置期限。读取超时可重试；写入超时必须转未知结果，保留操作编号，取消浏览器等待不等于取消服务端副作用。当前没有实测网络悬挂耗时，属于待验证的恢复风险 |
| 加载／空状态规则不一致 | 目录选择器有 `role=status`，但装配目录初次加载缺少 `busy` 提示；部分页面仅禁用按钮或显示空数据区域 | 使用下方状态容器区分首次读取、已有数据刷新、空结果、权限错误及离线；不把未加载显示为“没有数据” |
| 查询规模随全量目录增长 | [agent-library.ts](../../../frontend/src/features/agent/agent-library.ts) 自动遍历全部游标页，每页复制并排序累计数组；每个已挂载选择器独立读取 | 小目录可接受，重复游标已有停止保护。扩容时先限制一次读取的页数／条数并提供明确“继续加载”，再按查询键共享只读结果或服务端搜索；不要用无限缓存替代当前授权复核 |
| 页面保留带来的内存增长 | 页面宿主按会话保留页面；聊天还按房间保留嵌套配置与工作区实例 | 保留草稿有明确价值。测量访问大量房间后的 DOM、监听器和内存；仅淘汰无草稿、无在途及无未知结果的视图。不要为节省内存销毁未确认操作 |
| DTO 与请求策略散落 | 页面中重复定义 DTO、`run`、错误分支、回执收束和加载函数 | 先把端点合同与业务 composable 按功能抽离，优先修复已出现的策略漂移。不要一次性生成“万能 CRUD 页面” |

这些风险不等于已经出现性能故障。当前浏览器检查只能证明选定页面在给定数据量下的行为，不能得出完整生产性能结论。

## 建议的组件与目录边界

逐步建立以下位置；现有文件可在相关功能改动时迁入并同步引用，无需一次性搬动全部代码。

```text
frontend/src/
  app/                       # 认证外壳、页面宿主、受控导航
  shared/api/                # 同源传输、错误类别、期限；不包含业务成功判断
  shared/ui/                 # 状态容器、字段框架、页签、回执恢复展示
  features/chat/             # 聊天 DTO/decoder、发送状态、目录、消息与输入组件
  features/agent/            # 装配、工作区及各资料面板
  features/configuration/    # 配置草稿、应用与回执
  features/runtime/          # 计时器、任务、日志及各自查询
```

权限、CSRF、业务版本和操作编号继续由原服务端合同约束。页面负责组合，feature composable 管数据与恢复，展示组件不自行拼 API 路径。跨 feature 的目标类型移到明确的协议模块，避免通过另一页 `.vue` 导入领域 DTO；当前 `AgentTarget` 就定义在工作区组件里。

以下接口是**待实施方案**，当前仓库尚无这些新组件；例子不能作为已交付功能。

### 1. ResourceStatePanel：统一读取反馈

```ts
export type ResourcePhase = 'idle' | 'loading' | 'ready' | 'refreshing' | 'error'
export interface ResourceStatePanelProps {
  phase: ResourcePhase
  label: string
  empty: boolean
  hasData: boolean
  error?: { kind: 'network' | 'forbidden' | 'invalid-response'; message: string }
  retryable?: boolean
}
// emits：retry: []
// slots：default、empty、loading、error({error})
```

首次读取显示可感知的加载状态；刷新保留旧数据并标记正在更新；只有成功且无条目才显示空结果。`aria-busy` 放在被更新区域，短状态使用 `role=status`，错误使用适当的告警语义。它不处理写入，也不自动重试。

### 2. OperationRecoveryPanel：展示未知结果并保持原操作身份

```ts
export type OperationView =
  | { phase: 'idle' }
  | { phase: 'submitting'; operationId: string }
  | { phase: 'unknown'; operationId: string; message: string }
  | { phase: 'confirmed'; operationId: string; note: string }
  | { phase: 'rejected'; operationId: string; message: string }

export interface OperationRecoveryPanelProps {
  title: string
  state: OperationView
  canQuery: boolean
  canRetryOriginal: boolean
  busy: boolean
}
// emits：query: []、retryOriginal: []、abandon: []
// slots：confirmed({operationId,note})、details
```

组件只呈现恢复动作；原请求由业务 composable 保存，不能在组件中重新生成 UUID、自动重发或清空草稿。`abandon` 是放弃本地恢复信息，父级必须确认并说明不撤销既有副作用。`confirmed` 与“刷新当前数据失败”是两个独立状态。

每种写端点另有 decoder，返回 `unknown` 的网络结果必须在该边界校验。例如：

```ts
interface PendingChat {
  room: string
  body: Readonly<{
    operationId: string; profile: string; text: string; media: string
    reply: string; mention: boolean; observedAt: string
  }>
}
interface ChatReceipt {
  operationId: string
  messageId: string
  status: 'PERSISTED' | 'DISPATCHED' | 'REJECTED' | 'INDETERMINATE'
  note: string
}
// 校验字段类型、原操作编号和端点允许状态；拒绝非匹配结果。
declare function decodeChatReceipt(value: unknown, original: PendingChat): ChatReceipt
```

不要直接复用 Agent 管理回执的状态集合来处理聊天或计时器。共享的是生命周期和错误规则，各端点保持自己的数据合同。

### 3. ConfigFieldFrame：复用可访问的字段结构

```ts
export interface ConfigFieldFrameProps {
  id: string              // 父级实例前缀 + 字段稳定标识，同页不得重复
  label: string
  description?: string
  error?: string
  required?: boolean
  disabled?: boolean
}
// emits：无；不拥有 modelValue
// default slot：{controlId,labelledBy,describedBy,invalid,required,disabled}
// help slot：补充说明
```

在 `FileFieldEditor` 和组件装配表单外层复用该框架，继续使用 `ElInput`、`ElSwitch`、`ElInputNumber`、`ValueEditor`。描述和错误通过稳定 ID 关联到实际可聚焦节点；如组件库把 ARIA 属性落到包装层，必须按最终 DOM 修正。敏感字段仍不回显原值，不能为了统一组件绕过原草稿合同。

### 4. ResourceTabs：复用页签交互

```ts
export interface ResourceTab { id: string; label: string; disabled?: boolean }
export interface ResourceTabsProps {
  modelValue: string
  items: readonly ResourceTab[]
  ariaLabel: string
  idPrefix: string
  busy?: boolean
}
// emits：requestChange: [id: string]
// panel slot：{id,panelId,labelledBy,active}
```

优先包裹本项目已使用的 `ElTabs`，避免自己维护第二套键盘组件。使用手动激活：方向键只移动焦点，Enter/Space 发出切换意图；父级完成草稿／未决操作检查后才更新 `modelValue`。不得因焦点移动自动提交业务动作或丢失当前表单。

### 使用样例

```vue
<script setup lang="ts">
// 方案示例：复用原计时器业务状态，组件不自行执行 HTTP。
const timerState = useTimerManagement() // 返回 reactive 控制器，由现有 RuntimeView 的请求逻辑抽取
</script>

<template>
  <ResourceTabs
    :model-value="timerState.panel"
    :items="timerState.tabs"
    aria-label="运行管理分区"
    id-prefix="runtime"
    @request-change="timerState.requestPanel"
  >
    <template #panel="{id,panelId,labelledBy,active}">
      <section :id="panelId" role="tabpanel" :aria-labelledby="labelledBy" :hidden="!active">
        <ResourceStatePanel
          v-if="id === 'timers'"
          :phase="timerState.read.phase"
          label="定时规则"
          :empty="timerState.items.length === 0"
          :has-data="timerState.read.hasData"
          :error="timerState.read.error"
          :retryable="timerState.read.retryable"
          @retry="timerState.reload"
        >
          <TimerList :items="timerState.items" @change="timerState.confirmChange" />
        </ResourceStatePanel>
      </section>
    </template>
  </ResourceTabs>
  <OperationRecoveryPanel
    title="定时器操作"
    :state="timerState.operation"
    :can-query="timerState.canQuery"
    :can-retry-original="timerState.canRetryOriginal"
    :busy="timerState.busy"
    @query="timerState.queryOriginal"
    @retry-original="timerState.retryOriginal"
    @abandon="timerState.confirmAbandon"
  />
</template>
```

```vue
<ConfigFieldFrame :id="fieldId" :label="field.label" :description="field.description" :error="fieldError">
  <template #default="a11y">
    <ElSwitch
      :id="a11y.controlId"
      :model-value="enabled"
      :aria-labelledby="a11y.labelledBy"
      :aria-describedby="a11y.describedBy"
      :aria-invalid="a11y.invalid"
      :disabled="a11y.disabled"
      @update:model-value="editEnabled"
    />
  </template>
</ConfigFieldFrame>
```

这些样例展示职责与属性合同，不要求为每个业务新建抽象层。组件验收必须挂载真实组件库并检查最终可访问性树，不能只断言模板里存在 ARIA 字符串。

## 状态与边界验收矩阵

| 场景 | UI 必须表现 | 数据与操作约束 |
| --- | --- | --- |
| 首次读取 | 有加载文案和程序可识别的忙状态；不提前显示空结果 | 有期限；可离开；迟到结果不覆盖新目标 |
| 有数据时刷新 | 保留现有数据，明确正在刷新 | 刷新失败提示数据可能过时，不清空当前编辑 |
| 成功空结果 | 显示具体空状态和可用下一步 | 区分无数据、筛选不命中、未登记与无权限 |
| 401／403 | 401 回到登录并销毁旧会话视图；403 说明该动作不可用 | 不靠前端隐藏控件授权，不自动循环重试 |
| 网络失败／超时 | 读取可重试；写入显示结果未知 | 保留原参数和编号，不因 Abort 认定服务器回滚 |
| 坏 JSON／字段缺失／错操作编号 | 明确结果未确认，不显示成功 | 未确认前不能释放 `pending`、正文、附图和引用 |
| 版本冲突 | 显示确定冲突，可重新读取并由用户重做决定 | 不静默覆盖、不把新请求当成原请求重试 |
| 切目标／切页／注销 | 保持焦点可预测，必要时明确确认 | 已保留页继续保留草稿；销毁页不接收旧会话回调 |
| 重复点击／取消／刷新失败 | 按钮和状态匹配实际在途工作 | 一次逻辑操作使用一个编号；已确认结果不被后续读取失败推翻 |
| 大列表／大量房间 | 分页和截断可见，定位与搜索范围明确 | 不无限渲染、不无条件预加载所有目录页、不淘汰未决状态 |

## 前端生产评估标准

| 条目 | 首发最低门槛 | 当前证据状态 |
| --- | --- | --- |
| 写入恢复 | 所有副作用端点有运行时回执验证，未知结果可按原操作恢复 | **未满足：F01 已复现** |
| 组件可访问名称 | 每个可聚焦输入、开关和图标操作能通过名称辨认；说明／错误可关联 | **未满足：F02 已复现**；其他字段不能据此一概判失败或通过 |
| 键盘与焦点 | 关键流程无需鼠标；焦点可见；对话框关闭后返回触发点；组件遵守所声明的交互模式 | **部分满足：F03 待修**；真实读屏、完整焦点流程尚未验收 |
| 状态反馈 | 加载、空、权限、错误、未知结果和刷新过期状态可区分；重要状态变更可感知 | 已有局部实现，需按上方矩阵统一复核；适用 WCAG 4.1.3 |
| 响应式与缩放 | 320 CSS px 下关键内容与操作可达；文本放大、200%／400% 缩放不遮挡；需要二维关系的内容按规则处理 | 本轮仅日志页 390px／320px 无页面横向溢出；完整关键流程仍待验收，适用 WCAG 1.4.4、1.4.10 |
| 颜色与点击目标 | 默认主题和允许的主题组合核验文本／控件对比度；交互目标与焦点满足适用 AA 条件 | 暂无完整度量；不能以用了 Element Plus 代替验收，涉及 1.4.3、1.4.11、2.4.7、2.4.11、2.5.8 |
| 页面扩展 | 新页面通过声明接入，不改宿主的业务条件分支；真实授权由后端检查 | 当前结构基本满足，已存在单一页面目录和统一生命周期 |
| 模块边界 | 端点 decoder、业务状态和 UI 展示有清晰归属；共享组件不嵌入具体业务 API | 部分满足；按 F01 及大页面职责逐步拆分 |
| 生命周期 | 切目标、卸载和失去激活后无旧数据覆盖、无多重轮询；写请求不误当作撤销 | 已有 generation、abort、active polling 和草稿守卫；需在扩容负载下测量保留实例数量 |
| 构建与回归 | 类型检查及构建通过；关键动作在真实浏览器中覆盖网络异常、边界和键盘；产物版本一致 | 既有构建流程存在；本轮未重跑整套构建／生产包检查，不能扩大独立诊断范围 |

标准以 [WCAG 2.2 AA](https://www.w3.org/TR/WCAG22/) 的适用条款作为验收目标，不构成已经达标或完成认证的声明。自动化的可访问性树检查不能替代读屏与完整人工流程。

## 本轮验证证据

- [真实源码回执诊断](../../../temp/verification/4.8.6-l/mvp-review/frontend/receipt-diagnostics.mjs) 执行当前 `api.ts` 及两页真实 `run/deliver/receive`；只替换网络、响应式容器和刷新副作用。共 6 个场景：4 个无效回执场景失败并复现 F01，2 个有效回执对照通过。结果见 [receipt-diagnostics.json](../../../temp/verification/4.8.6-l/mvp-review/frontend/receipt-diagnostics.json)。
- [真实 Vue 浏览器诊断](../../../temp/verification/4.8.6-l/mvp-review/frontend/browser-diagnostics.mjs) 使用本机无界面 Chrome 和替身 API。确认无名字段、页签模式缺口、正常空目录和选定窄屏布局；最终页面异常列表为空。结果见 [browser-diagnostics.json](../../../temp/verification/4.8.6-l/mvp-review/frontend/browser-diagnostics.json)。初次日志替身缺少 `policy` 的异常在补齐测试合同后消失，没有登记为产品缺陷。
- 未连接真实后端、数据库或模型，未执行真实写入。未完成所有主题对比度、真实读屏、移动端触控、全站自动无障碍扫描及完整生产分包验收。
- 最终收尾核对中，`api.ts`、`WebChatView.vue`、`RuntimeView.vue`、`AgentAssemblyEditor.vue`、`ChatSettingsView.vue` 的 SHA256 仍与本轮诊断快照一致。`AgentWorkspaceView.vue` 经并行提交后摘要发生变化；只将其 `management-receipts` 多行导入在内存中还原为原单行形式，即能得到原快照摘要，确认变化仅为导入排版。已重读 F01–F03 关键片段，工作区页签缺口仍在，并将源码定位从 369 行更新为 373 行。原浏览器诊断范围不变，没有重复执行浏览器诊断或 Gradle，也没有覆盖原快照。

临时文件可能不进入 Git，因此 F01–F03 的触发条件、源位置及修改边界已直接保存在本文。
