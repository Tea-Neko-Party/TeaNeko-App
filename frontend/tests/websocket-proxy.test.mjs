import {test} from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import https from 'node:https'
import {createHash} from 'node:crypto'
import {fileURLToPath} from 'node:url'
import {createServer, loadConfigFromFile} from 'vite'

const { config } = await loadConfigFromFile({ command: 'serve', mode: 'development' },
  fileURLToPath(new URL('../vite.config.ts', import.meta.url)))

// 2026/11/04：通过真实 TLS 与升级代理验证认证头保留及拒绝响应，不连接实际机器人。
test('本地 WSS 转发保留认证和加密来源，传回握手成功或认证拒绝', { timeout: 15000 }, async () => {
  const backend = http.createServer()
  const seen = []
  backend.on('upgrade', (request, socket) => {
    seen.push(request)
    if (request.headers.authorization !== 'Bearer development-machine-token') {
      socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n')
      return
    }
    const accept = createHash('sha1').update(request.headers['sec-websocket-key']
      + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64')
    socket.end('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n'
      + `Sec-WebSocket-Accept: ${accept}\r\n\r\n`)
  })
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
  const frontend = await createServer({ ...config, configFile: false,
    root: fileURLToPath(new URL('../', import.meta.url)),
    cacheDir: fileURLToPath(new URL('../../build/tmp/websocket-proxy/vite-cache', import.meta.url)),
    server: { ...config.server, host: '127.0.0.1', port: 0, strictPort: false,
      proxy: { '/ws/': { ...config.server.proxy['/ws/'],
        target: `ws://127.0.0.1:${backend.address().port}` } } },
  })
  try {
    await frontend.listen()
    const connect = authorization => new Promise((resolve, reject) => {
      const request = https.request({ host: '127.0.0.1', port: frontend.httpServer.address().port,
        path: '/ws/onebot', rejectUnauthorized: false, agent: false,
        headers: { Authorization: authorization, Connection: 'Upgrade', Upgrade: 'websocket',
          'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ==', 'Sec-WebSocket-Version': '13' },
      }, response => { response.resume(); resolve(response.statusCode) })
      request.on('upgrade', (response, socket) => { socket.destroy(); resolve(response.statusCode) })
      request.on('error', reject)
      request.setTimeout(5000, () => request.destroy(new Error('测试握手超时')))
      request.end()
    })
    assert.equal(await connect('Bearer development-machine-token'), 101)
    assert.equal(await connect('Bearer invalid-token'), 401)
    assert.equal(seen[0].url, '/ws/onebot')
    assert.equal(seen[0].headers.authorization, 'Bearer development-machine-token')
    assert.equal(seen[0].headers['x-forwarded-proto'], 'https')
    assert.equal(seen[0].headers['x-forwarded-host'], `localhost:${config.server.port}`)
  } finally {
    await frontend.close()
    await new Promise(resolve => backend.close(resolve))
  }
})
