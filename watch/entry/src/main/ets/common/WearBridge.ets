import common from '@ohos.app.ability.common';
import { Account } from './Types';

/**
 * 手表端 Wear Engine 接收桥 —— 当前为占位实现。
 *
 * 原因：API 9 SDK（HarmonyOS 3.1）不内置 `@ohos.wearengine` 模块，
 * 穿戴侧 Wear Engine SDK 需在华为开发者平台申请 Wear Engine 权限后单独获取。
 * 申请获批并集成 SDK 后，恢复本文件底部的原始实现即可（接口保持不变）。
 *
 * 开发期导账号替代方案：用 hdc 向手表 files 目录推送 accounts.json
 * （手机端「同步到手表」页可生成该文件），AccountStore 启动时会自动读取。
 */
export class WearBridge {
  /** 最近一次连接状态描述，供设置页展示 */
  static lastStatus: string = '未集成 Wear Engine（等待权限审批）';
  private static started: boolean = false;

  static start(
    ctx: common.UIAbilityContext,
    onAccounts: (list: Account[]) => void,
    onStatus: (msg: string) => void
  ): void {
    if (WearBridge.started) {
      return;
    }
    WearBridge.started = true;
    onStatus(WearBridge.lastStatus);
  }
}

/*
===================== 原始 Wear Engine 实现（获批集成后恢复） =====================

import wearEngine from '@ohos.wearengine';
import util from '@ohos.util';
import common from '@ohos.app.ability.common';
import { Account } from './Types';
import { AccountStore } from './AccountStore';

const PHONE_BUNDLE: string = 'com.wanged.tfa';
const PHONE_FINGERPRINT: string = ''; // TODO: 填入手机端应用签名证书的 SHA-256 指纹

interface Frame {
  v: number;
  ty: string;
  id: string;
  i: number;
  n: number;
  b: string;
}

export class WearBridge {
  private static buffer: Map<string, Map<number, Uint8Array>> = new Map<string, Map<number, Uint8Array>>();
  private static onAccounts: (list: Account[]) => void = (list: Account[]) => {
  };
  private static onStatus: (msg: string) => void = (msg: string) => {
  };
  private static started: boolean = false;

  static start(
    ctx: common.UIAbilityContext,
    onAccounts: (list: Account[]) => void,
    onStatus: (msg: string) => void
  ): void {
    WearBridge.onAccounts = onAccounts;
    WearBridge.onStatus = onStatus;

    if (PHONE_FINGERPRINT.length === 0) {
      onStatus('未配置手机端签名指纹，请先编辑 WearBridge.ets');
      return;
    }
    if (WearBridge.started) {
      return;
    }
    WearBridge.started = true;

    try {
      const deviceClient = wearEngine.getDeviceClient(ctx);
      deviceClient.getConnectedDevices().then((devices: wearEngine.Device[]) => {
        if (!devices || devices.length === 0) {
          onStatus('未发现已连接手机');
          return;
        }
        const device: wearEngine.Device = devices[0];
        const p2pClient = wearEngine.getP2pClient(ctx);
        const remoteApp: wearEngine.AppInfo = { bundleName: PHONE_BUNDLE, fingerprint: PHONE_FINGERPRINT };
        const appParam: wearEngine.P2pAppParam = { remoteApp: remoteApp };
        const callback = (message: wearEngine.P2pMessage): void => {
          WearBridge.handleMessage(message);
        };
        p2pClient.registerMessageReceiver(device.randomId, appParam, callback)
          .then(() => {
            onStatus('已连接手机，等待同步…');
          })
          .catch((err: Error) => {
            onStatus('注册消息接收失败: ' + err.message);
          });
      }).catch((err: Error) => {
        onStatus('查询连接设备失败: ' + err.message);
      });
    } catch (err) {
      onStatus('Wear Engine 不可用: ' + JSON.stringify(err));
    }
  }

  private static handleMessage(message: wearEngine.P2pMessage): void {
    try {
      const decoder = util.TextDecoder.create('utf-8');
      const text: string = decoder.decodeWithStream(message.content);
      const frame: Frame = JSON.parse(text) as Frame;
      if (frame.ty !== 'data' || frame.n <= 0) {
        return;
      }

      let chunks: Map<number, Uint8Array> | undefined = WearBridge.buffer.get(frame.id);
      if (chunks === undefined) {
        chunks = new Map<number, Uint8Array>();
        WearBridge.buffer.set(frame.id, chunks);
      }
      const helper = new util.Base64Helper();
      chunks.set(frame.i, helper.decodeSync(frame.b));

      if (chunks.size === frame.n) {
        const assembled: Uint8Array | undefined = WearBridge.assemble(chunks, frame.n);
        WearBridge.buffer.delete(frame.id);
        if (assembled === undefined) {
          WearBridge.onStatus('数据重组失败');
          return;
        }
        const payloadText: string = decoder.decodeWithStream(assembled);
        const list: Account[] = AccountStore.parseWire(payloadText);
        WearBridge.onAccounts(list);
      }
    } catch (err) {
      WearBridge.onStatus('解析同步消息失败: ' + JSON.stringify(err));
    }
  }

  private static assemble(chunks: Map<number, Uint8Array>, total: number): Uint8Array | undefined {
    const parts: Uint8Array[] = [];
    let length: number = 0;
    for (let i = 0; i < total; i++) {
      const part: Uint8Array | undefined = chunks.get(i);
      if (part === undefined) {
        return undefined;
      }
      parts.push(part);
      length += part.length;
    }
    const all: Uint8Array = new Uint8Array(length);
    let offset: number = 0;
    for (let i = 0; i < parts.length; i++) {
      all.set(parts[i], offset);
      offset += parts[i].length;
    }
    return all;
  }
}

==============================================================================
*/
