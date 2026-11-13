package ToFA.Android.whmwws.phone.data

/** 账号卡片显示名三档：只密钥名 → 只账户名 → 都显示。 */
enum class DisplayNameMode(val id: String, val label: String, val enLabel: String) {
    SecretOnly("secret", "只密钥名", "Secret name only"),
    AccountOnly("account", "只账户名", "Account only"),
    Both("both", "都显示", "Show both");

    companion object {
        fun fromId(id: String?): DisplayNameMode? = entries.firstOrNull { it.id == id }
    }
}
