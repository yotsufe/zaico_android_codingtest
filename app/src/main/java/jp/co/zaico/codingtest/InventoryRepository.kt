package jp.co.zaico.codingtest

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 在庫データ作成の窓口。ViewModel はこの interface にだけ依存する。 */
interface InventoryRepository {

    /** タイトルだけを指定して在庫データを作成する。失敗時は例外を投げる。 */
    suspend fun createInventory(title: String)
}

/**
 * zaico API v2 を叩く [InventoryRepository] の実装。
 *
 * HttpClient と company_id の解決をコンストラクタで差し替えられるようにしてあるため、
 * MockEngine を使えば実際の通信なしにリクエストの形を検証できる。
 */
class ZaicoInventoryRepository(
    private val endpoint: ZaicoApiEndpoint,
    private val httpClientFactory: () -> HttpClient = { ZaicoApi.newClient() },
    // ZaicoApi 側の company_id キャッシュはプロセス全体で共有されテスト間で漏れるため、
    // 解決処理そのものを差し替えられるようにしておく。
    private val companyIdProvider: suspend (HttpClient) -> Int = { client ->
        ZaicoApi.companyId(client, endpoint)
    },
) : InventoryRepository {

    override suspend fun createInventory(title: String) {
        httpClientFactory().use { client ->
            val companyId = companyIdProvider(client)

            // 公開 API v2 ドキュメントの Inventories_create に準拠する。
            // 必須パラメータは title のみ。
            // ドキュメントの成功ステータスは 201 だが実機は 200 を返すため、2xx を成功として扱う。
            ZaicoApi.postText(
                client = client,
                endpoint = endpoint,
                path = "/api/v2/orgs/companies/$companyId/inventories.json",
                jsonBody = buildJsonObject { put("title", title) }.toString(),
            )
        }
    }

    companion object {
        fun from(context: Context) = ZaicoInventoryRepository(ZaicoApiEndpoint.from(context))
    }
}
