// 2026/11/06：执行真实 API 与页面回执函数，检查未知结果是否仍保留可恢复请求。
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {createRequire} from 'node:module'
import vm from 'node:vm'

const require = createRequire(resolve('frontend/package.json'))
const ts = require('typescript')
const compile = source => ts.transpileModule(source, {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
const apiSource = readFileSync('frontend/src/api.ts','utf8')
const report = []
const decoders = vm.createContext({exports:{}, Error, Date})
vm.runInContext(compile(readFileSync('frontend/src/operation-receipts.ts', 'utf8')), decoders)

function componentFunctions(file, names) {
  const script = readFileSync(file,'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)?.[1]
  assert.ok(script)
  const tree = ts.createSourceFile(file,script,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
  return names.map(name=>{
    const node = tree.statements.find(item=>ts.isFunctionDeclaration(item)&&item.name?.text===name)
    assert.ok(node, `${file} 中必须存在 ${name}`)
    return node.getText(tree)
  }).join('\n')
}

function fixture(kind, response, refreshFails = false) {
  const requests=[]
  const api=vm.createContext({exports:{},Error,Headers,fetch:async(url,init)=>{
    if(String(url).endsWith('/auth/csrf')) return new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'review-only'}))
    requests.push({url,body:init.body?JSON.parse(init.body):undefined})
    return new Response(JSON.stringify(response),{status:200})
  }})
  vm.runInContext(compile(apiSource),api)
  const original=kind==='chat'?{room:'room-a',body:{operationId:'operation-a',text:'未提交聊天正文',profile:'member-a',media:'image-a'}}
    :{id:'timer-a',change:{operationId:'operation-a',revision:7,paused:true}}
  const state={pending:{value:original},receipt:{value:undefined},busy:{value:false},error:{value:''},
    generation:1,pendingFailed:{value:false},pendingAdmin:{value:undefined},pendingUpload:{value:undefined},
    text:{value:'未提交聊天正文'},media:{value:'image-a'},reply:{value:'reply-a'},mention:{value:true},
    ...decoders.exports, Error,ApiError:api.exports.ApiError,emit:()=>{},request:api.exports.request,
    base:id=>`chat/rooms/${id}`,pull:async()=>{if(refreshFails) throw new Error('刷新失败')},images:async()=>{},loadTimers:async()=>{if(refreshFails) throw new Error('刷新失败')},
    query:(action,body)=>({action,...body}),
    call:(name,body)=>api.exports.request(`management/${name}`,{method:'POST',body:JSON.stringify(body)})}
  const view=vm.createContext(state)
  const file=`frontend/src/${kind==='chat'?'WebChatView':'RuntimeView'}.vue`
  // 2026/11/07：查询恢复也执行页面的真实入口，不能只证明首次提交路径。
  vm.runInContext(compile(componentFunctions(file,['receive','deliver','run',kind==='chat'?'query':'operation'])),view,{filename:file})
  return {state,view,requests,original}
}
for (const kind of ['chat','runtime']) for (const flow of ['submit','recover']) {
  const valid=kind==='chat'?{operationId:'operation-a',messageId:'message-a',status:'DISPATCHED',note:'已受理'}
    :{operationId:'operation-a',timer:'timer-a',revision:8,paused:true,at:'2026-11-06T00:00:00Z',note:'已确认'}
  for(const [label,body,expected,refreshFails] of [['空对象',{},false],['错误操作编号',{...valid,operationId:'another-operation'},false],['错误字段',kind==='chat'?{...valid,messageId:''}:{...valid,paused:false},false],['无效状态',kind==='chat'?{...valid,status:'UNKNOWN'}:{...valid,revision:-1},false],['有效回执',valid,true],['确认后刷新失败',valid,true,true]]) {
    const tested=fixture(kind,body,refreshFails)
    if(flow==='submit') await tested.view.run(tested.view.deliver)
    else if(kind==='chat') await tested.view.run(tested.view.query)
    else await tested.view.operation()
    const observed={pendingRetained:tested.state.pending.value===tested.original,
      text:tested.state.text.value,media:tested.state.media.value,error:tested.state.error.value,requests:tested.requests.length}
    try {
      if(expected) assert.equal(tested.state.pending.value,undefined)
      else {
        assert.equal(tested.state.pending.value,tested.original,'未匹配的回执不得清除原请求')
        if(kind==='chat') {
          assert.equal(tested.state.text.value,'未提交聊天正文','未确认时不得清除正文')
          assert.equal(tested.state.media.value,'image-a')
          assert.equal(tested.state.reply.value,'reply-a')
        }
        assert.equal(tested.requests.length,1,'未知回执不得自动重试')
      }
      if(flow==='recover') assert.ok(kind==='chat'?tested.requests[0].url.endsWith('/operations/operation-a')
        :tested.requests[0].body.operationId==='operation-a','恢复查询必须使用原操作编号')
      report.push({kind,flow,label,passed:true,observed})
    } catch(failure) {report.push({kind,flow,label,passed:false,observed,failure:failure.message})}
  }
}
console.log(JSON.stringify(report,null,2))
if(report.some(item=>!item.passed)) process.exitCode=1
