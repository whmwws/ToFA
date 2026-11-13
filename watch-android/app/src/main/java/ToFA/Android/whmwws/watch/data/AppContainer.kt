package ToFA.Android.whmwws.watch.data

import android.content.Context

/** 进程内单例容器，避免 Compose 重组时反复创建仓库与密钥。 */
object AppContainer {

    @Volatile
    private var repository: AccountRepository? = null

    fun repository(context: Context): AccountRepository =
        repository ?: synchronized(this) {
            repository ?: AccountRepository(SecretVault(context.applicationContext)).also {
                it.ensureLoaded()
                repository = it
            }
        }
}
