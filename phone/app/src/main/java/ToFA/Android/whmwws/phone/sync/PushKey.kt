package ToFA.Android.whmwws.phone.sync

/** 手机推送账号时的加密方式，对应「扫描唯一码 / 自定义密码 / 无密码」三种选择。 */
sealed interface PushKey {
    data object None : PushKey
    data class Password(val value: String) : PushKey
    data class UniqueCode(val code: String) : PushKey
}
