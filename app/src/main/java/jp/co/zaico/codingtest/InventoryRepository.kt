package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 在庫データの窓口。ViewModel はこの interface にだけ依存する。 */
interface InventoryRepository {

    /** 在庫一覧を取得する。失敗時は例外を投げる。 */
    suspend fun getInventories(): List<Inventory>

    /** 在庫詳細を取得する。失敗時は例外を投げる。 */
    suspend fun getInventory(inventoryId: Int): Inventory

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
        ZaicoApi.resolveCompanyId(client, endpoint)
    },
) : InventoryRepository {

    override suspend fun getInventories(): List<Inventory> = withInventoriesPath { client, path ->
        ZaicoApi.parseData(ZaicoApi.getRawBody(client, endpoint, path))
            .jsonArray
            .map { ZaicoApi.toInventory(it.jsonObject) }
    }

    override suspend fun getInventory(inventoryId: Int): Inventory = withInventoriesPath { client, path ->
        val body = ZaicoApi.getRawBody(client, endpoint, "${path.removeSuffix(".json")}/$inventoryId.json")
        ZaicoApi.toInventory(ZaicoApi.parseData(body).jsonObject)
    }

    override suspend fun createInventory(title: String) {
        withInventoriesPath { client, path ->
            // 公開 API v2 ドキュメントの Inventories_create に準拠する。
            // 必須パラメータは title のみ。
            // ドキュメントの成功ステータスは 201 だが実機は 200 を返すため、2xx を成功として扱う。
            ZaicoApi.postRawBody(
                client = client,
                endpoint = endpoint,
                path = path,
                jsonBody = buildJsonObject { put("title", title) }.toString(),
            )
        }
    }

    /** クライアントの生成・解放と company_id の解決をまとめる。3 つの操作すべてが必要とするため。 */
    private suspend fun <T> withInventoriesPath(
        block: suspend (HttpClient, String) -> T,
    ): T = httpClientFactory().use { client ->
        val companyId = companyIdProvider(client)
        block(client, "/api/v2/orgs/companies/$companyId/inventories.json")
    }
}
