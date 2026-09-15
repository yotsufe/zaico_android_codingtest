package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2 の在庫エンドポイントが必要とする company_id を解決する。
 *
 * 一度取得したら保持する。寿命は Hilt の `@Singleton` に任せる。
 * 保持をクラスの内側に閉じることで、テストは新しいインスタンスを作るだけで初期状態に戻せる。
 *
 * [Mutex] で囲っているのは、同時に複数の画面が取得を始めたときに API を 2 回叩かないため。
 */
@Singleton
class CompanyIdProvider @Inject constructor(
    private val endpoint: ZaicoApiEndpoint,
) {

    private val mutex = Mutex()

    private var cached: Int? = null

    /**
     * 保持していればそれを返し、無ければ取得して保持する。
     *
     * 読み書きをすべてロックの内側に閉じている。ロックの外に高速パスを置けば
     * 2 回目以降は獲得を省けるが、直後に通信が走るこの経路で削れるのはナノ秒でしかなく、
     * その代わりに `@Volatile` とメモリモデルの理解が要る形になる。割に合わない。
     */
    suspend fun resolve(client: HttpClient): Int = mutex.withLock {
        cached ?: fetch(client).also { cached = it }
    }

    private suspend fun fetch(client: HttpClient): Int {
        val body = ZaicoApi.getRawBody(client, endpoint, "/api/v2/orgs/companies.json")
        val companies = ZaicoApi.parseDataAsArray(body)
        if (companies.isEmpty()) {
            throw ApiException.NoCompany()
        }
        return ZaicoApi.requireIntOf(companies.first(), part = JsonPart.COMPANY, key = "id")
    }
}
