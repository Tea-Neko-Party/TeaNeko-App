import {test} from 'node:test'
import assert from 'node:assert/strict'
import {rememberLoginToken, savedLoginToken, takeLoginToken} from '../src/login-token.ts'

// 2026/11/04：使用浏览器接口夹具验证链接消费和显式持久化，不引入 DOM 测试依赖。
function browser(href) {
  const values = new Map()
  globalThis.window = {
    location: { href },
    history: { state: null, replaceState(_state, _title, url) { window.location.href = String(url) } },
    localStorage: {
      getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key),
    },
  }
}

test('片段优先填入，消费后移除查询和片段令牌并保留其他参数', () => {
  browser('https://console.example/console/?view=chat&token=old#token=new%2Bsecret&tab=main')
  assert.equal(takeLoginToken(), 'new+secret')
  assert.equal(window.location.href, 'https://console.example/console/?view=chat#tab=main')
  assert.equal(takeLoginToken(), '')
  browser('https://console.example/console/?token=query-secret')
  assert.equal(takeLoginToken(), 'query-secret')
  assert.equal(window.location.href, 'https://console.example/console/')
})

test('明确保存后可读取，取消保存会清除', () => {
  browser('https://console.example/console/')
  assert.equal(savedLoginToken(), '')
  assert.equal(rememberLoginToken('saved-secret', true), true)
  assert.equal(savedLoginToken(), 'saved-secret')
  assert.equal(rememberLoginToken('saved-secret', false), true)
  assert.equal(savedLoginToken(), '')
})

test('浏览器禁用存储时保留手动登录并报告保存失败', () => {
  browser('https://console.example/console/')
  Object.defineProperty(window, 'localStorage', { get() { throw new Error('存储已禁用') } })
  assert.equal(savedLoginToken(), '')
  assert.equal(rememberLoginToken('secret', true), false)
})
