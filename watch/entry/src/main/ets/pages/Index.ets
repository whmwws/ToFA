import router from '@ohos.router';
import common from '@ohos.app.ability.common';
import { Account } from '../common/Types';
import { Totp } from '../common/Totp';
import { AccountStore } from '../common/AccountStore';
import { WearBridge } from '../common/WearBridge';

/**
 * 手表端主页面：单账号视图 + 六位验证码 + 二维码导出按钮 + 环形倒计时进度条。
 * 右侧小点指示账号数量与当前位次；旋转表冠/上下滑动切换账号（竖向聚焦 Swiper）。
 * 长按页面进入设置菜单。
 * 数据来源：手机端 Wear Engine 下发（WearBridge）、或开发期 hdc 推送的 files/accounts.json。
 */
@Entry
@Component
struct Index {
  @State accounts: Account[] = [];
  @State codes: string[] = [];
  @State remains: number[] = [];
  @State currentIndex: number = 0;
  @State status: string = '正在加载…';
  private timerId: number = -1;
  private busy: boolean = false;

  aboutToAppear(): void {
    this.bootstrap();
    this.timerId = setInterval(() => {
      this.tick();
    }, 1000);
  }

  aboutToDisappear(): void {
    if (this.timerId >= 0) {
      clearInterval(this.timerId);
      this.timerId = -1;
    }
  }

  onPageShow(): void {
    // 从设置页返回时刷新账号列表
    const ctx = getContext(this) as common.UIAbilityContext;
    AccountStore.load(ctx).then((list: Account[]) => {
      this.accounts = list;
      this.tick();
    });
  }

  private async bootstrap(): Promise<void> {
    const ctx = getContext(this) as common.UIAbilityContext;
    this.accounts = await AccountStore.load(ctx);
    this.status = this.accounts.length > 0 ? `共 ${this.accounts.length} 个账号` : '暂无账号，请在手机上同步';
    await this.tick();

    WearBridge.start(ctx,
      (list: Account[]) => {
        this.accounts = list;
        AccountStore.save(ctx, list);
        this.status = `已同步 ${list.length} 个账号`;
        this.tick();
      },
      (msg: string) => {
        this.status = msg;
      });
  }

  private async tick(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    const now: number = Date.now();
    const nextCodes: string[] = [];
    const nextRemains: number[] = [];
    for (let i = 0; i < this.accounts.length; i++) {
      const acc: Account = this.accounts[i];
      try {
        nextCodes.push(await Totp.code(acc, now));
      } catch (err) {
        nextCodes.push('------');
      }
      const period: number = acc.period > 0 ? acc.period : 30;
      nextRemains.push(acc.type === 'HOTP' ? period : Totp.secondsLeft(period, now));
    }
    this.codes = nextCodes;
    this.remains = nextRemains;
    this.busy = false;
  }

  private formatCode(code: string): string {
    if (code.length === 6) {
      return code.substring(0, 3) + ' ' + code.substring(3);
    }
    if (code.length === 8) {
      return code.substring(0, 4) + ' ' + code.substring(4);
    }
    return code;
  }

  @Builder
  accountPage(acc: Account, index: number) {
    Column({ space: 6 }) {
      Text(acc.issuer.length > 0 ? acc.issuer : (acc.account.length > 0 ? acc.account : '未命名'))
        .fontSize(12)
        .fontColor('#8A8F98')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(index < this.codes.length ? this.formatCode(this.codes[index]) : '------')
        .fontSize(32)
        .fontColor('#7BD88F')
        .fontWeight(FontWeight.Bold)
        .textAlign(TextAlign.Center)

      Row({ space: 18 }) {
        Button('二维码')
          .fontSize(12)
          .height(34)
          .onClick(() => {
            router.push({
              url: 'pages/ExportQr',
              params: { index: this.currentIndex.toString() }
            });
          })

        Stack() {
          Progress({
            value: index < this.remains.length ? this.remains[index] : (acc.period > 0 ? acc.period : 30),
            total: acc.period > 0 ? acc.period : 30,
            type: ProgressType.Ring
          })
            .width(48)
            .height(48)
            .color('#7BD88F')
            .backgroundColor('#22262F')
            .style({ strokeWidth: 4 })
          Text(acc.type === 'HOTP' ? '—' : (index < this.remains.length ? this.remains[index].toString() : ''))
            .fontSize(13)
            .fontColor('#E6E8EC')
        }
      }
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .padding({ left: 26, right: 26, bottom: 10 })
  }

  build() {
    Stack() {
      if (this.accounts.length === 0) {
        Column({ space: 8 }) {
          Text('暂无账号')
            .fontSize(14)
            .fontColor('#8A8F98')
          Text(this.status)
            .fontSize(10)
            .fontColor('#5F6672')
            .textAlign(TextAlign.Center)
          Text('长按进入设置')
            .fontSize(10)
            .fontColor('#5F6672')
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .padding(24)
      } else {
        // 竖向 Swiper：每个账号一页；表冠旋转/上下滑动切换
        Swiper() {
          ForEach(this.accounts, (acc: Account, index: number) => {
            this.accountPage(acc, index)
          }, (acc: Account) => acc.id)
        }
        .vertical(true)
        .loop(true)
        .indicator(false)
        .focusable(true)
        .defaultFocus(true)
        .width('100%')
        .height('100%')
        .onChange((index: number) => {
          this.currentIndex = index;
        })

        // 右侧账号指示点（透传触摸）
        Row() {
          Blank()
          Column({ space: 6 }) {
            ForEach(this.accounts, (acc: Account, index: number) => {
              Circle({ width: 6, height: 6 })
                .fill(index === this.currentIndex ? '#7BD88F' : '#3A3F4A')
            }, (acc: Account) => 'dot_' + acc.id)
          }
          .justifyContent(FlexAlign.Center)
        }
        .width('100%')
        .height('100%')
        .padding({ right: 7 })
        .hitTestBehavior(HitTestMode.Transparent)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#000000')
    .gesture(
      LongPressGesture({ duration: 800 })
        .onAction(() => {
          router.push({ url: 'pages/Settings' });
        })
    )
  }
}
