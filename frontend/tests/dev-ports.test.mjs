import {test} from 'node:test'
import assert from 'node:assert/strict'
import {developmentPorts} from '../dev-ports.ts'

// 2026/11/04：验证统一端口来源、Spring 实际值覆盖及监听冲突。
test('读取统一端口，并允许后端实际生效值覆盖源码默认值', () => {
  // 2026/11/07：夹具包含现行必需的 WebSocket 端口，零沿用实际 HTTP 后端端口。
  const properties = 'server.port=24691\ntea-neko.web.frontend.port=25173\ntea-neko.websocket.port=0\n'
  assert.deepEqual(developmentPorts(properties, {}),
    { backend: 24691, frontend: 25173, websocket: 24691, authority: 'localhost:25173' })
  assert.deepEqual(developmentPorts(properties,
    { TEANEKO_BACKEND_PORT: '32001', TEANEKO_FRONTEND_PORT: '32002' }),
    { backend: 32001, frontend: 32002, websocket: 32001, authority: 'localhost:32002' })
  assert.throws(() => developmentPorts(properties, { TEANEKO_FRONTEND_PORT: '24691' }), /同一个端口/)
  assert.throws(() => developmentPorts(properties, { TEANEKO_BACKEND_PORT: '70000' }), /server.port/)
})
