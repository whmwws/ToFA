// 生成 DevEco 风格签名材料（material/fd|ac|ce）并输出加密后的密码 hex
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const component = new Int8Array([49, 243, 9, 115, 214, 175, 91, 184, 211, 190, 177, 88, 101, 131, 192, 119]);
const materialDir = process.argv[2]; // 例如 E:\WangEd\2FA\watch\signature\material
const passwords = process.argv.slice(3); // 要加密的明文密码列表

function encrypt(keyBytes, plain) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-128-gcm', Buffer.from(keyBytes), iv);
  const enc = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  const head = Buffer.alloc(4);
  head.writeUInt32BE(enc.length + 16, 0); // hvigor: 头 = 密文+tag 总长度
  return Buffer.concat([head, iv, enc, tag]);
}

function xorBytes(arrays) {
  const len = arrays[0].length;
  const out = Buffer.alloc(len);
  for (let i = 0; i < len; i++) {
    let v = 0;
    for (const a of arrays) v ^= a[i];
    out[i] = v;
  }
  return out;
}

fs.rmSync(materialDir, { recursive: true, force: true });
const fd = path.join(materialDir, 'fd');
const ac = path.join(materialDir, 'ac');
const ce = path.join(materialDir, 'ce');
fs.mkdirSync(fd, { recursive: true });
fs.mkdirSync(ac, { recursive: true });
fs.mkdirSync(ce, { recursive: true });

// fd: 3 个目录，每个目录 1 个 16 字节随机文件
const fdParts = [];
for (let i = 0; i < 3; i++) {
  const sub = path.join(fd, `d${i}`);
  fs.mkdirSync(sub, { recursive: true });
  const b = crypto.randomBytes(16);
  fs.writeFileSync(path.join(sub, `f${i}`), b);
  fdParts.push(new Int8Array(b));
}
// ac: 盐（目录下直接 1 个文件）
const salt = crypto.randomBytes(16);
fs.writeFileSync(path.join(ac, 's0'), salt);

const xored = xorBytes([...fdParts.map(a => Buffer.from(a)), Buffer.from(component)]);
const rootKey = crypto.pbkdf2Sync(xored.toString(), salt, 10000, 16, 'sha256');

// ce: 工作密钥密文（目录下直接 1 个文件）
const workKey = crypto.randomBytes(16);
fs.writeFileSync(path.join(ce, 'w0'), encrypt(rootKey, workKey));

for (const p of passwords) {
  console.log(encrypt(workKey, Buffer.from(p, 'utf-8')).toString('hex'));
}
