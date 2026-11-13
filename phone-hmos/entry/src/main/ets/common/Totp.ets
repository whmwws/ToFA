import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { Account } from './Types';
import { Base32 } from './Base32';

/** RFC 4226 / RFC 6238，与安卓端 TotpEngine.kt 同一套算法与参数。 */
export class Totp {
  static async code(acc: Account, atMs: number): Promise<string> {
    const period: number = acc.period > 0 ? acc.period : 30;
    const counter: number = acc.type === 'HOTP' ? acc.counter : Math.floor(atMs / 1000 / period);
    const key: Uint8Array = Base32.decode(acc.secret);
    const message: Uint8Array = Totp.counterBytes(counter);
    const hash: Uint8Array = await Totp.hmac(Totp.macAlgorithm(acc.algorithm), key, message);
    return Totp.truncate(hash, acc.digits === 8 ? 8 : 6);
  }

  static secondsLeft(period: number, atMs: number): number {
    const p: number = period > 0 ? period : 30;
    const elapsed: number = Math.floor(atMs / 1000) % p;
    return p - elapsed;
  }

  private static macAlgorithm(algorithm: string): string {
    const alg: string = algorithm.toUpperCase();
    if (alg === 'SHA256') {
      return 'SHA256';
    }
    if (alg === 'SHA512') {
      return 'SHA512';
    }
    return 'SHA1';
  }

  private static async hmac(alg: string, key: Uint8Array, message: Uint8Array): Promise<Uint8Array> {
    const generator = cryptoFramework.createSymKeyGenerator('HMAC');
    const keyBlob: cryptoFramework.DataBlob = { data: key };
    const symKey = await generator.convertKey(keyBlob);
    const mac = cryptoFramework.createMac(alg);
    await mac.init(symKey);
    await mac.update({ data: message });
    const result = await mac.doFinal();
    return result.data;
  }

  private static counterBytes(counter: number): Uint8Array {
    const bytes: Uint8Array = new Uint8Array(8);
    let value: number = counter;
    for (let i = 7; i >= 0; i--) {
      bytes[i] = value % 256;
      value = Math.floor(value / 256);
    }
    return bytes;
  }

  private static truncate(hash: Uint8Array, digits: number): string {
    const offset: number = hash[hash.length - 1] & 0x0f;
    const binary: number =
      (hash[offset] & 0x7f) * 16777216 +
      (hash[offset + 1] & 0xff) * 65536 +
      (hash[offset + 2] & 0xff) * 256 +
      (hash[offset + 3] & 0xff);
    const modulo: number = digits === 8 ? 100000000 : 1000000;
    let otp: string = Math.floor(binary % modulo).toString();
    while (otp.length < digits) {
      otp = '0' + otp;
    }
    return otp;
  }
}
