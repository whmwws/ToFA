import router from '@ohos.router';
import common from '@ohos.app.ability.common';
import promptAction from '@ohos.promptAction';
import { Account } from '../common/Types';
import { AccountStore } from '../common/AccountStore';
import { WearBridge } from '../common/WearBridge';

/**
 * 设置菜单（主页面长按进入）：
 * - 连接手机：查看/触发与手机端 App 的同步
 * - 重新加载账号：重新读取本地存储（含 hdc 推送的 accounts.json）
 * - 清空账号
 */
@Entry
@Component
struct Settings {
  @State accountCount: number = 0;
  @State syncStatus: string = WearBridge.lastStatus;

  aboutToAppear(): void {
    const ctx = getContext(this) as common.UIAbilityContext;
    AccountStore.load(ctx).then((list: Account[]) => {
      this.accountCount = list.length;
    });
  }

  build() {
    List({ space: 10 }) {
      ListItem() {
        Text('设置')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#E6E8EC')
          .width('100%')
          .textAlign(TextAlign.Center)
      }

      ListItem() {
        Row({ space: 8 }) {
          Text('连接手机')
            .fontSize(14)
            .fontColor('#E6E8EC')
          Blank()
          Text(this.syncStatus.length > 0 ? this.syncStatus : '未连接')
            .fontSize(10)
            .fontColor('#5F6672')
            .maxLines(2)
            .textAlign(TextAlign.End)
            .layoutWeight(1)
        }
        .width('100%')
        .padding(12)
        .backgroundColor('#14171C')
        .borderRadius(12)
        .onClick(() => {
          this.syncStatus = WearBridge.lastStatus;
          promptAction.showToast({ message: '请在手机端 ToFA 打开「同步到手表」' });
        })
      }

      ListItem() {
        Row({ space: 8 }) {
          Text('账号数量')
            .fontSize(14)
            .fontColor('#E6E8EC')
          Blank()
          Text(this.accountCount.toString())
            .fontSize(12)
            .fontColor('#8A8F98')
        }
        .width('100%')
        .padding(12)
        .backgroundColor('#14171C')
        .borderRadius(12)
      }

      ListItem() {
        Text('重新加载账号')
          .fontSize(14)
          .fontColor('#E6E8EC')
          .width('100%')
          .padding(12)
          .backgroundColor('#14171C')
          .borderRadius(12)
          .onClick(() => {
            const ctx = getContext(this) as common.UIAbilityContext;
            AccountStore.load(ctx).then((list: Account[]) => {
              this.accountCount = list.length;
              promptAction.showToast({ message: `已加载 ${list.length} 个账号` });
            });
          })
      }

      ListItem() {
        Text('清空账号')
          .fontSize(14)
          .fontColor('#FF6B60')
          .width('100%')
          .padding(12)
          .backgroundColor('#14171C')
          .borderRadius(12)
          .onClick(() => {
            const ctx = getContext(this) as common.UIAbilityContext;
            AccountStore.save(ctx, []).then(() => {
              this.accountCount = 0;
              promptAction.showToast({ message: '已清空' });
            });
          })
      }

      ListItem() {
        Button('返回')
          .fontSize(13)
          .onClick(() => {
            router.back();
          })
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 14, right: 14, top: 24, bottom: 24 })
    .backgroundColor('#000000')
  }
}
