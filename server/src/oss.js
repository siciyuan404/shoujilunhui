// OSS 存储层：通过 rclone 对接阿里云 OSS（s3 兼容，私有桶）。
// 配置在 config.json 的 oss 段：
//   {
//     "oss": {
//       "remote": "aliyunoss",              // rclone 远端名
//       "bucket": "allshouji",              // OSS 桶名
//       "prefix": "uploads",                // 桶内目录前缀
//       "rclone": "C:\\...\\rclone.exe"     // rclone 可执行文件完整路径（不在 PATH 时必填）
//     }
//   }
// 未配置 oss 段或配置不完整时，enabled() 返回 false，服务回退到本地 uploads 存储。
const { spawn } = require('child_process');

let _cfg = null;

function init(cfg) {
  _cfg = (cfg && cfg.oss) ? cfg.oss : null;
}

function enabled() {
  return !!( _cfg && _cfg.remote && _cfg.bucket && _cfg.rclone );
}

function bin() {
  return _cfg.rclone || 'rclone';
}

function keyOf(fname) {
  const prefix = String(_cfg.prefix || 'uploads').replace(/^\/+|\/+$/g, '');
  return prefix ? prefix + '/' + fname : fname;
}

function remoteOf(key) {
  return `${_cfg.remote}:${_cfg.bucket}/${key}`;
}

// 执行 rclone 命令，成功返回 stdout，失败 reject（含 stderr）
function run(args) {
  return new Promise((resolve, reject) => {
    let p;
    try {
      p = spawn(bin(), args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      return reject(new Error('无法启动 rclone：' + e.message));
    }
    let out = '', err = '';
    p.stdout.on('data', (c) => { out += c; });
    p.stderr.on('data', (c) => { err += c; });
    p.on('error', (e) => reject(new Error('rclone 启动失败：' + e.message)));
    p.on('close', (code) => {
      if (code === 0) resolve(out);
      else reject(new Error(`rclone ${args[0]} 失败(code=${code})：` + (err.trim() || out.trim())));
    });
  });
}

// 上传本地文件到 OSS 前缀，返回桶内 key（如 uploads/u_xxx.jpg）
async function upload(localFile, fname) {
  const key = keyOf(fname);
  await run(['copyto', localFile, remoteOf(key)]);
  return key;
}

// 读取 OSS 文件内容；不存在或读取失败返回 null（上层按 404 处理）
async function read(fname) {
  const key = keyOf(fname);
  return new Promise((resolve) => {
    let p;
    try {
      p = spawn(bin(), ['cat', remoteOf(key)], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      return resolve(null);
    }
    const chunks = [];
    let err = '';
    p.stdout.on('data', (c) => chunks.push(c));
    p.stderr.on('data', (c) => { err += c; });
    p.on('error', () => resolve(null));
    p.on('close', (code) => {
      if (code === 0) return resolve(Buffer.concat(chunks));
      return resolve(null); // 对象不存在 / 网络错误 → 视为无
    });
  });
}

// 删除 OSS 上的单个文件
async function remove(fname) {
  const key = keyOf(fname);
  await run(['deletefile', remoteOf(key)]);
  return key;
}

module.exports = { init, enabled, upload, read, remove, keyOf };
