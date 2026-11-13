import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';
import { Account } from './Types';

/**
 * 账号持久化（对应安卓端 SecretVault + AccountRepository）：
 * 明文 JSON 经 AES-256/GCM 加密后写入 preferences；
 * 32 字节主密钥首次随机生成并 Base64 存 preferences（依赖应用沙箱隔离，卸载即不可恢复）。
 * 存盘格式：Base64( IV(12) | cipherText | authTag(16) )
 */
export class AccountStore {
  private static readonly STORE = 'tfa_accounts';
  private static readonly KEY_DATA = 'accounts';
  private static readonly KEY_MASTER = 'vault_key';
  private static readonly ALG = 'AES256';
  private static readonly IV_LEN = 12;
  private static readonly TAG_LEN = 16;

  static async load(ctx: common.UIAbilityContext): Promise<Account[]> {
    try {
      const store = preferences.getPreferencesSync(ctx, { name: AccountStore.STORE });
      const raw = store.getSync(AccountStore.KEY_DATA, '') as string;
      if (raw.length === 0) {
        return [];
      }
      const plain = await AccountStore.decrypt(ctx, raw);
      return JSON.parse(plain) as Account[];
    } catch (err) {
      console.error('读取账号失败: ' + JSON.stringify(err));
      return [];
    }
  }

  static async save(ctx: common.UIAbilityContext, list: Account[]): Promise<void> {
    try {
      const store = preferences.getPreferencesSync(ctx, { name: AccountStore.STORE });
      const encrypted = await AccountStore.encrypt(ctx, JSON.stringify(list));
      store.putSync(AccountStore.KEY_DATA, encrypted);
      store.flush();
    } catch (err) {
      console.error('保存账号失败: ' + JSON.stringify(err));
    }
  }

  private static gcmSpec(iv: Uint8Array, tag: Uint8Array): cryptoFramework.GcmParamsSpec {
    const spec: cryptoFramework.GcmParamsSpec = {
      iv: { data: iv },
      aad: { data: new Uint8Array(0) },
      authTag: { data: tag },
      algName: 'GcmParamsSpec'
    };
    return spec;
  }

  private static async encrypt(ctx: common.UIAbilityContext, plain: string): Promise<string> {
    const key = await AccountStore.masterKey(ctx);
    const iv = cryptoFramework.createRandom().generateRandomSync(AccountStore.IV_LEN).data;
    const cipher = cryptoFramework.createCipher('AES256|GCM|PKCS7');
    await cipher.init(cryptoFramework.CryptoMode.ENCRYPT_MODE, key, AccountStore.gcmSpec(iv, new Uint8Array(0)));
    const plainBytes = new util.TextEncoder().encodeInto(plain);
    const out = await cipher.doFinal({ data: plainBytes });
    const data = out.data;
    const cipherLen = data.length - AccountStore.TAG_LEN;
    const packed = new Uint8Array(AccountStore.IV_LEN + data.length);
    packed.set(iv, 0);
    packed.set(data.subarray(0, cipherLen), AccountStore.IV_LEN);
    packed.set(data.subarray(cipherLen), AccountStore.IV_LEN + cipherLen);
    return AccountStore.b64(packed);
  }

  private static async decrypt(ctx: common.UIAbilityContext, b64: string): Promise<string> {
    const packed = AccountStore.unb64(b64);
    const iv = packed.subarray(0, AccountStore.IV_LEN);
    const cipherLen = packed.length - AccountStore.IV_LEN - AccountStore.TAG_LEN;
    const cipherText = packed.subarray(AccountStore.IV_LEN, AccountStore.IV_LEN + cipherLen);
    const tag = packed.subarray(AccountStore.IV_LEN + cipherLen);
    const cipher = cryptoFramework.createCipher('AES256|GCM|PKCS7');
    await cipher.init(cryptoFramework.CryptoMode.DECRYPT_MODE, await AccountStore.masterKey(ctx), AccountStore.gcmSpec(iv, tag));
    const out = await cipher.doFinal({ data: cipherText });
    return util.TextDecoder.create('utf-8').decodeToString(out.data);
  }

  /** 生成或读取 32 字节主密钥 */
  private static async masterKey(ctx: common.UIAbilityContext): Promise<cryptoFramework.SymKey> {
    const store = preferences.getPreferencesSync(ctx, { name: AccountStore.STORE });
    let b64 = store.getSync(AccountStore.KEY_MASTER, '') as string;
    if (b64.length === 0) {
      const raw = cryptoFramework.createRandom().generateRandomSync(32).data;
      b64 = AccountStore.b64(raw);
      store.putSync(AccountStore.KEY_MASTER, b64);
      store.flush();
    }
    const gen = cryptoFramework.createSymKeyGenerator(AccountStore.ALG);
    return gen.convertKey({ data: AccountStore.unb64(b64) });
  }

  private static b64(data: Uint8Array): string {
    return new util.Base64Helper().encodeToStringSync(data);
  }

  private static unb64(text: string): Uint8Array {
    return new util.Base64Helper().decodeSync(text);
  }
}
