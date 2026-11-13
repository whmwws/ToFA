import router from '@ohos.router';
import common from '@ohos.app.ability.common';
import { Account } from '../common/Types';
import { AccountStore } from '../common/AccountStore';

/**
 * 二维码导出页：把当前账号编码为 otpauth:// URI 并渲染二维码，
 * 供其他设备的验证器 App 扫码导入。轻触任意处返回。
 */
@Entry
@Component
struct ExportQr {
  @State title: string = '';
  @State uri: string = '';

  aboutToAppear(): void {
    const ctx = getContext(this) as common.UIAbilityContext;
    AccountStore.load(ctx).then((list: Account[]) => {
      let index = 0;
      const params = router.getParams() as Record<string, string>;
      if (params !== undefined && params !== null && params['index'] !== undefined) {
        const parsed = parseInt(params['index']);
        if (!isNaN(parsed)) {
          index = parsed;
        }
      }
      if (index >= 0 && index < list.length) {
        const acc: Account = list[index];
        this.title = acc.issuer.length > 0 ? acc.issuer : acc.account;
        this.uri = this.buildOtpAuthUri(acc);
      }
    });
  }

  private buildOtpAuthUri(acc: Account): string {
    const label: string = encodeURIComponent(
      (acc.issuer.length > 0 ? acc.issuer + ':' : '') + (acc.account.length > 0 ? acc.account : acc.issuer));
    const scheme: string = acc.type === 'HOTP' ? 'hotp' : 'totp';
    let uri: string = `otpauth://${scheme}/${label}?secret=${acc.secret.toUpperCase()}`;
    if (acc.issuer.length > 0) {
      uri += `&issuer=${encodeURIComponent(acc.issuer)}`;
    }
    const alg: string = acc.algorithm.toUpperCase();
    if (alg === 'SHA256' || alg === 'SHA512') {
      uri += `&algorithm=${alg}`;
    }
    if (acc.digits === 8) {
      uri += '&digits=8';
    }
    if (acc.type === 'HOTP') {
      uri += `&counter=${acc.counter}`;
    } else if (acc.period > 0 && acc.period !== 30) {
      uri += `&period=${acc.period}`;
    }
    return uri;
  }

  build() {
    Column({ space: 10 }) {
      Text(this.title)
        .fontSize(14)
        .fontColor('#E6E8EC')
        .maxLines(1)
      if (this.uri.length > 0) {
        Column() {
          QRCode(this.uri)
            .width(150)
            .height(150)
        }
        .padding(10)
        .backgroundColor(Color.White)
        .borderRadius(10)
      } else {
        Text('无账号')
          .fontSize(13)
          .fontColor('#8A8F98')
      }
      Text('用其他设备的验证器扫码导入')
        .fontSize(10)
        .fontColor('#5F6672')
      Text('轻触返回')
        .fontSize(10)
        .fontColor('#3A3F4A')
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#000000')
    .onClick(() => {
      router.back();
    })
  }
}
