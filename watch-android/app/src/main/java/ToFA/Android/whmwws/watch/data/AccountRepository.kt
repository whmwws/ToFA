package ToFA.Android.whmwws.watch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 账号仓库：内存 StateFlow 供 UI 订阅，每次变更后立即加密落盘。 */
class AccountRepository(private val vault: SecretVault) {

    private val _accounts = MutableStateFlow<List<TotpAccount>>(emptyList())
    val accounts: StateFlow<List<TotpAccount>> = _accounts.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _accounts.value = TotpAccount.listFromJson(vault.load().orEmpty())
    }

    fun upsert(account: TotpAccount) {
        ensureLoaded()
        val current = _accounts.value
        val existing = current.indexOfFirst { it.id == account.id }
        val next = if (existing >= 0) {
            current.toMutableList().also { it[existing] = account }
        } else {
            current + account
        }
        commit(next)
    }

    fun upsertAll(list: List<TotpAccount>) {
        ensureLoaded()
        var next = _accounts.value
        list.forEach { account ->
            val existing = next.indexOfFirst {
                it.id == account.id || (it.issuer == account.issuer && it.account == account.account && it.secret == account.secret)
            }
            next = if (existing >= 0) next.toMutableList().also { it[existing] = account } else next + account
        }
        commit(next)
    }

    fun delete(id: String) {
        ensureLoaded()
        commit(_accounts.value.filterNot { it.id == id })
    }

    /** 拖动排序：把 [from] 位置的账号移动到 [to]，顺序随加密落盘持久化。 */
    fun move(from: Int, to: Int) {
        ensureLoaded()
        val current = _accounts.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val next = current.toMutableList()
        val item = next.removeAt(from)
        next.add(to.coerceIn(0, next.size), item)
        commit(next)
    }

    /** 清空全部密钥（紧急密码 / 错误次数上限触发）。 */
    fun wipe() {
        loaded = true
        _accounts.value = emptyList()
        vault.clear()
    }

    private fun commit(list: List<TotpAccount>) {
        _accounts.value = list
        vault.save(TotpAccount.listToJson(list))
    }
}
