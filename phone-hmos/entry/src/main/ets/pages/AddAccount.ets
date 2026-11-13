import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { Account } from '../common/Types';
import { AccountStore } from '../common/AccountStore';
import { OtpauthUri } from '../common/OtpauthUri';
import { Base32 } from '../common/Base32';

@Entry
@Component
struct AddAccount {
  @State issuer: string = '';
  @State account: string = '';
  @State secret: string = '';
  @State algorithm: string = 'SHA1';
  @State digits: number = 6;
  @State period: number = 30;
  @State type: string = 'TOTP';
  @State otpauth: string = '';
  @State error: string = '';

  private getContext(): common.UIAbilityContext {
    return getContext(this) as common.UIAbilityContext;
  }

  @Builder
  chip(label: string, selected: boolean, onTap: () => void) {
    Text(label)
      .fontSize(13)
      .fontColor(selected ? '#0F1115' : '#E6E8EC')
      .padding({ left: 14, right: 14, top: 7, bottom: 7 })
      .backgroundColor(selected ? '#7BD88F' : '#22262F')
      .borderRadius(14)
      .onClick(onTap)
  }

  @Builder
  field(label: string, value: string, hint: string, onChange: (v: string) => void) {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(12)
        .fontColor('#8A8F98')
      TextInput({ text: value, placeholder: label })
        .placeholderColor('#5F6672')
        .fontColor('#E6E8EC')
        .backgroundColor('#171B24')
        .borderRadius(10)
        .height(44)
        .padding({ left: 12, right: 12 })
        .onChange(onChange)
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
  }

  parseOtpauth(): void {
    this.error = '';
    const parsed = OtpauthUri.parse(this.otpauth);
    if (parsed === null) {
      this.error = '无法识别该 otpauth 链接';
      return;
    }
    this.issuer = parsed.issuer;
    this.account = parsed.account;
    this.secret = parsed.secret;
    this.algorithm = parsed.algorithm;
    this.digits = parsed.digits;
    this.period = parsed.period;
    this.type = parsed.type;
    this.otpauth = '';
  }

  async save(): Promise<void> {
    this.error = '';
    const secret = this.secret.split(' ').join('').split('-').join('').toUpperCase();
    if (secret.length === 0) {
      this.error = '请填写密钥';
      return;
    }
    try {
      Base32.decode(secret);
    } catch (e) {
      this.error = '密钥不是合法的 Base32：' + (e as Error).message;
      return;
    }
    const acc: Account = {
      id: `${this.issuer}-${this.account}-${secret.substring(0, 4)}-${Date.now()}`,
      issuer: this.issuer.trim(),
      account: this.account.trim(),
      secret: secret,
      algorithm: this.algorithm,
      digits: this.digits,
      period: this.period,
      type: this.type,
      counter: 0
    };
    const ctx = this.getContext();
    const list = await AccountStore.load(ctx);
    list.push(acc);
    await AccountStore.save(ctx, list);
    router.back();
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(26)
          .fontColor('#E6E8EC')
          .padding(8)
          .onClick(() => router.back())
        Text('添加账号')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#E6E8EC')
        Blank()
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 14 })

      Scroll() {
        Column({ space: 14 }) {
          Column({ space: 6 }) {
            Text('粘贴 otpauth:// 链接自动填充')
              .fontSize(12)
              .fontColor('#8A8F98')
            Row({ space: 8 }) {
              TextInput({ text: this.otpauth, placeholder: 'otpauth://totp/…?secret=…' })
                .placeholderColor('#5F6672')
                .fontColor('#E6E8EC')
                .backgroundColor('#171B24')
                .borderRadius(10)
                .height(44)
                .padding({ left: 12, right: 12 })
                .layoutWeight(1)
                .onChange((v: string) => this.otpauth = v)
              Button('解析')
                .fontSize(13)
                .height(44)
                .backgroundColor('#7BD88F')
                .fontColor('#0F1115')
                .onClick(() => this.parseOtpauth())
            }
            .width('100%')
          }
          .alignItems(HorizontalAlign.Start)
          .width('100%')

          this.field('发行方（如 GitHub / Google）', this.issuer, '发行方', (v: string) => this.issuer = v)
          this.field('账号（邮箱 / 用户名）', this.account, '账号', (v: string) => this.account = v)
          this.field('密钥（Base32，可含空格）', this.secret, 'JBSWY3DPEHPK3PXP', (v: string) => this.secret = v)

          Column({ space: 6 }) {
            Text('类型').fontSize(12).fontColor('#8A8F98')
            Row({ space: 8 }) {
              this.chip('TOTP', this.type === 'TOTP', () => this.type = 'TOTP')
              this.chip('HOTP', this.type === 'HOTP', () => this.type = 'HOTP')
            }
          }
          .alignItems(HorizontalAlign.Start)
          .width('100%')

          Column({ space: 6 }) {
            Text('算法').fontSize(12).fontColor('#8A8F98')
            Row({ space: 8 }) {
              this.chip('SHA1', this.algorithm === 'SHA1', () => this.algorithm = 'SHA1')
              this.chip('SHA256', this.algorithm === 'SHA256', () => this.algorithm = 'SHA256')
              this.chip('SHA512', this.algorithm === 'SHA512', () => this.algorithm = 'SHA512')
            }
          }
          .alignItems(HorizontalAlign.Start)
          .width('100%')

          Row() {
            Column({ space: 6 }) {
              Text('位数').fontSize(12).fontColor('#8A8F98')
              Row({ space: 8 }) {
                this.chip('6', this.digits === 6, () => this.digits = 6)
                this.chip('8', this.digits === 8, () => this.digits = 8)
              }
            }
            .alignItems(HorizontalAlign.Start)
            Blank()
            Column({ space: 6 }) {
              Text('周期（秒）').fontSize(12).fontColor('#8A8F98')
              Row({ space: 8 }) {
                this.chip('30', this.period === 30, () => this.period = 30)
                this.chip('60', this.period === 60, () => this.period = 60)
              }
            }
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')

          if (this.error.length > 0) {
            Text(this.error)
              .fontSize(13)
              .fontColor('#FF7A7A')
          }

          Button('保存')
            .width('100%')
            .height(48)
            .fontSize(16)
            .backgroundColor('#7BD88F')
            .fontColor('#0F1115')
            .onClick(() => this.save())
        }
        .width('100%')
        .padding({ left: 20, right: 20, bottom: 40 })
      }
      .width('100%')
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0F1115')
  }
}
