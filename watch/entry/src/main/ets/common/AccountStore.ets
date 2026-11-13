import preferences from '@ohos.data.preferences';
import fs from '@ohos.file.fs';
import common from '@ohos.app.ability.common';
import { Account, WirePayload, wireToAccount } from './Types';

const STORE_NAME: string = 'tfa_accounts';
const STORE_KEY: string = 'accounts';

/**
 * 账号持久化。
 * 1) 正常情况：preferences 存储；
 * 2) 首次启动且 preferences 为空时，尝试读取 filesDir/accounts.json
 *    （开发期可用 hdc 直接推送，见手机端「同步到手表」页的排查清单）。
 */
export class AccountStore {
  static async load(ctx: common.UIAbilityContext): Promise<Account[]> {
    try {
      const store = await preferences.getPreferences(ctx, STORE_NAME);
      const raw = await store.get(STORE_KEY, '') as string;
      if (raw.length > 0) {
        const list: Account[] = JSON.parse(raw) as Account[];
        if (list.length > 0) {
          return list;
        }
      }
    } catch (err) {
      console.error('读取本地账号失败: ' + JSON.stringify(err));
    }
    return AccountStore.loadFromFile(ctx);
  }

  static async save(ctx: common.UIAbilityContext, list: Account[]): Promise<void> {
    try {
      const store = await preferences.getPreferences(ctx, STORE_NAME);
      await store.put(STORE_KEY, JSON.stringify(list));
      await store.flush();
    } catch (err) {
      console.error('保存账号失败: ' + JSON.stringify(err));
    }
  }

  /** 解析手机端 SyncCodec 生成的负载 */
  static parseWire(json: string): Account[] {
    const payload: WirePayload = JSON.parse(json) as WirePayload;
    const result: Account[] = [];
    if (!payload || !payload.accounts) {
      return result;
    }
    for (let i = 0; i < payload.accounts.length; i++) {
      const acc: Account = wireToAccount(payload.accounts[i]);
      if (acc.secret.length > 0) {
        result.push(acc);
      }
    }
    return result;
  }

  private static loadFromFile(ctx: common.UIAbilityContext): Account[] {
    const path: string = ctx.filesDir + '/accounts.json';
    try {
      if (!fs.accessSync(path)) {
        return [];
      }
      const text: string = fs.readTextSync(path);
      const list: Account[] = AccountStore.parseWire(text);
      if (list.length > 0) {
        // 读入后转存到 preferences，避免每次启动都依赖该文件
        AccountStore.save(ctx, list);
      }
      return list;
    } catch (err) {
      console.error('读取 accounts.json 失败: ' + JSON.stringify(err));
      return [];
    }
  }
}
