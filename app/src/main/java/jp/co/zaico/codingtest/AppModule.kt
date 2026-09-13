package jp.co.zaico.codingtest

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** アプリ全体で共有する依存の組み立て。 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideZaicoApiEndpoint(@ApplicationContext context: Context): ZaicoApiEndpoint =
        ZaicoApiEndpoint.from(context)

    /**
     * Dagger は Kotlin のデフォルト引数を解釈しないため、`@Binds` ではなくここで組み立てる。
     *
     * `ZaicoInventoryRepository` の `httpClientFactory` / `companyIdProvider` は
     * テスト用の差し替え口で、本番に対応する束縛が存在しない。
     */
    @Provides
    @Singleton
    fun provideInventoryRepository(endpoint: ZaicoApiEndpoint): InventoryRepository =
        ZaicoInventoryRepository(endpoint)
}
