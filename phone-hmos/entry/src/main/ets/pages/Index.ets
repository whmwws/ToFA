import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { Account } from '../common/Types';
import { AccountStore } from '../common/AccountStore';
import { Totp } from '../common/Totp';

@Entry
@Component
struct Index {
  @State accounts: Account[] = [];
  @State codes: string[] = [];
  @State remains: number[] = [];
  @State copiedId: string = '';
  private timer: number = -1;

  aboutToAppear(): void {
    this.reload();
    this.tick();
    this.timer = setInterval(() => this.tick(), 1000);
  }

  aboutToDisappear(): void {
    if (this.timer >= 0) {
      clearInterval(this.timer);
      this.timer = -1;
    }
  }

  private getContext(): common.UIAbilityContext {
    return getContext(this) as common.UIAbilityContext;
  }

  async reload(): Promise<void> {
    this.accounts = await AccountStore.load(this.getContext());
    this.tick();
  }

  async tick(): Promise<void> {
    const now = Date.now();
    const codes: string[] = [];
    const remains: number[] = [];
    for (const acc of this.accounts) {
      try {
        codes.push(await Totp.code(acc, now));
      } catch {
        codes.push('------');
      }
      remains.push(Totp.secondsLeft(acc.period, now));
    }
    this.codes = codes;
    this.remains = remains;
  }

  formatCode(code: string): string {
    if (code.length === 6) {
      return code.substring(0, 3) + ' ' + code.substring(3);
    }
    if (code.length === 8) {
      return code.substring(0, 4) + ' ' + code.substring(4);
    }
    return code;
  }

  @Builder
  accountCard(acc: Account, index: number) {
    Column({ space: 6 }) {
      Row() {
        Column({ space: 2 }) {
          Text(acc.issuer.length > 0 ? acc.issuer : '未命名')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor('#E6E8EC')
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(acc.account)
            .fontSize(12)
            .fontColor('#8A8F98')
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Blank()

        Button() {
          Text('删')
            .fontSize(12)
            .fontColor('#FF7A7A')
        }
        .type(ButtonType.Normal)
        .backgroundColor('#2A2F3A')
        .width(40)
        .height(28)
        .onClick(async () => {
          const next = this.accounts.filter((_, i) => i !== index);
          this.accounts = next;
          await AccountStore.save(this.getContext(), next);
          this.tick();
        })
      }
      .width('100%')

      Row() {
        Text(index < this.codes.length ? this.formatCode(this.codes[index]) : '------')
          .fontSize(34)
          .fontWeight(FontWeight.Bold)
          .fontColor('#7BD88F')
          .fontFamily('HarmonyOS Sans')
        Blank()
        Text(this.remains[index] + 's')
          .fontSize(13)
          .fontColor('#8A8F98')
      }
      .width('100%')

      Progress({ value: index < this.remains.length ? this.remains[index] : 30, total: acc.period > 0 ? acc.period : 30, type: ProgressType.Linear })
        .color('#7BD88F')
        .backgroundColor('#22262F')
        .style({ strokeWidth: 4 })
        .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#171B24')
    .borderRadius(16)
    .onClick(() => {
      this.copiedId = acc.id;
    })
  }

  build() {
    Navigation() {
      Column() {
        Row() {
          Column({ space: 2 }) {
            Text('2FA 验证器')
              .fontSize(26)
              .fontWeight(FontWeight.Bold)
              .fontColor('#E6E8EC')
            Text(`${this.accounts.length} 个账号`)
              .fontSize(12)
              .fontColor('#8A8F98')
          }
          .alignItems(HorizontalAlign.Start)
          Blank()
        }
        .width('100%')
        .padding({ left: 20, right: 20, top: 18, bottom: 10 })

        if (this.accounts.length === 0) {
          Column({ space: 10 }) {
            Text('还没有账号')
              .fontSize(16)
              .fontColor('#8A8F98')
            Text('点右下角 + 手动输入密钥，或粘贴 otpauth:// 链接')
              .fontSize(13)
              .fontColor('#5F6672')
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List({ space: 12 }) {
            ForEach(this.accounts, (acc: Account, index: number) => {
              ListItem() {
                this.accountCard(acc, index)
              }
            }, (acc: Account) => acc.id)
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ left: 16, right: 16, bottom: 90 })
        }
      }
      .width('100%')
      .height('100%')

      Row() {
        Blank()
        Button() {
          Text('+')
            .fontSize(28)
            .fontColor('#0F1115')
            .fontWeight(FontWeight.Bold)
        }
        .width(56)
        .height(56)
        .type(ButtonType.Circle)
        .backgroundColor('#7BD88F')
        .margin({ right: 22, bottom: 28 })
        .onClick(() => {
          router.pushUrl({ url: 'pages/AddAccount' });
        })
      }
      .width('100%')
      .position({ x: 0, y: '100%' })
      .translate({ x: 0, y: '-100%' })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0F1115')
    .hideTitleBar(true)
  }
}
