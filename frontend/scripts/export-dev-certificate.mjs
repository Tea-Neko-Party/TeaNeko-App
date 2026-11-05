import {X509Certificate} from 'node:crypto'
import {readFile, writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'

// 2026/11/05：只导出 Vite 当前开发证书的公钥证书，供本机机器客户端建立信任；不复制私钥。
const source = new URL('../node_modules/.vite/basic-ssl/_cert.pem', import.meta.url)
const target = new URL('../node_modules/.vite/basic-ssl/teaneko-dev-ca.pem', import.meta.url)
const pem = await readFile(source, 'utf8').catch(failure => {
  if (failure.code === 'ENOENT') {
    throw new Error('尚未找到开发证书，请先启动前端开发服务，再执行证书导出。')
  }
  throw failure
})
const certificate = new X509Certificate(pem)
await writeFile(target, certificate.toString() + '\n', {mode: 0o600})
console.log(`已导出开发公钥证书：${fileURLToPath(target)}`)
console.log('请由本机 OneBot 客户端信任此证书；开发证书更新后需重新导出并重启客户端。')
