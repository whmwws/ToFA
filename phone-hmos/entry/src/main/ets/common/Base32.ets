const ALPHABET: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** RFC 4648 Base32 解码，忽略空格、连字符、大小写与补位 */
export class Base32 {
  static decode(input: string): Uint8Array {
    const clean: string = input.split(' ').join('').split('-').join('').split('=').join('').toUpperCase();
    if (clean.length === 0) {
      throw new Error('密钥为空');
    }
    const out: number[] = [];
    let buffer: number = 0;
    let bitsLeft: number = 0;
    for (let i = 0; i < clean.length; i++) {
      const v: number = ALPHABET.indexOf(clean.charAt(i));
      if (v < 0) {
        throw new Error('密钥包含非法字符: ' + clean.charAt(i));
      }
      buffer = (buffer << 5) | v;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out.push((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    const result: Uint8Array = new Uint8Array(out.length);
    for (let i = 0; i < out.length; i++) {
      result[i] = out[i];
    }
    return result;
  }
}
