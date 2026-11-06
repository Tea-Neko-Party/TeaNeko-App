// 2026/11/06：编辑器声明与表单分离，扩展输入方式只需登记，不复制配置页面。
import type {Extension} from '@codemirror/state'

export interface ValueEditorDefinition {
  name: string
  extensions: () => Extension[] | Promise<Extension[]>
  validate: (text: string) => string
  format?: (text: string) => string
}
const definitions = new Map<string, ValueEditorDefinition>()

/** 登记一种编辑器，重复标识不能按加载顺序覆盖。 */
export function registerValueEditor(id: string, definition: ValueEditorDefinition) {
  if (definitions.has(id)) throw new Error('编辑器重复登记：' + id)
  definitions.set(id, definition)
}
/** 未知编辑器保留可编辑原文，并由调用方显示未支持提示。 */
export function valueEditor(id: string) { return definitions.get(id) }

/** 格式化只变更空白，不通过 JS 数值重新编码，保留 Long、大整数、指数和重复键。 */
export function formatJSON(text: string): string {
  JSON.parse(text)
  const tokens = text.match(/"(?:\\.|[^"\\])*"|true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\],:]/g) ?? []
  let result = ''
  let depth = 0
  const line = () => '\n' + '  '.repeat(depth)
  for (let index = 0; index < tokens.length; index++) {
    const token = tokens[index]!
    if (token === '{' || token === '[') {
      result += token
      depth++
      if (tokens[index + 1] !== '}' && tokens[index + 1] !== ']') result += line()
    } else if (token === '}' || token === ']') {
      depth--
      if (tokens[index - 1] !== '{' && tokens[index - 1] !== '[') result += line()
      result += token
    } else if (token === ',') result += ',' + line()
    else if (token === ':') result += ': '
    else result += token
  }
  return result
}

registerValueEditor('JSON', {
  name: 'JSON 编辑器', extensions: async () => {
    const [{json, jsonParseLinter}, {linter}] = await Promise.all([import('@codemirror/lang-json'), import('@codemirror/lint')])
    return [json(), linter(jsonParseLinter())]
  },
  validate(text) {
    try { JSON.parse(text); return '' }
    catch (failure) { return failure instanceof Error ? failure.message : 'JSON 格式无效。' }
  },
  format: formatJSON
})
registerValueEditor('LONG_TEXT', {name: '文本编辑器', extensions: () => [], validate: () => ''})
