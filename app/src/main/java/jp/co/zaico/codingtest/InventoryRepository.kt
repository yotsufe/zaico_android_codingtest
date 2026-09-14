package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 在庫データの窓口。ViewModel はこの interface にだけ依存する。 */
interface InventoryRepository {

    /** 在庫一覧を取得する。失敗時は例外を投げる。 */
    suspend fun getInventories(): Inventories

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

    override suspend fun getInventories(): Inventories = withInventoriesBasePath { client, base ->
        val elements = ZaicoApi.parseDataAsArray(ZaicoApi.getRawBody(client, endpoint, "$base.json"))
        // 1 件でも形が違えば全件を失うのは避ける。ただし黙って減らすと在庫が欠けたことに
        // 気づけないため、読み飛ばした件数を呼び出し側へ渡す。
        val items = elements.mapNotNull {
            // ApiException に絞る。runCatching だと実装ミス由来の例外まで
            // 「形が違う 1 件」として無言で読み飛ばしてしまう。
            try {
                ZaicoApi.toInventory(it)
            } catch (e: ApiException) {
                null
            }
        }
        Inventories(items = items, skipped = elements.size - items.size)
    }

    override suspend fun getInventory(inventoryId: Int): Inventory = withInventoriesBasePath { client, base ->
        val body = ZaicoApi.getRawBody(client, endpoint, "$base/$inventoryId.json")
        ZaicoApi.toInventory(ZaicoApi.parseDataAsObject(body))
    }

    override suspend fun createInventory(title: String) {
        withInventoriesBasePath { client, base ->
            // 公開 API v2 ドキュメントの Inventories_create に準拠する。
            // 必須パラメータは title のみ。
            // ドキュメントの成功ステータスは 201 だが実機は 200 を返すため、2xx を成功として扱う。
            ZaicoApi.postRawBody(
                client = client,
                endpoint = endpoint,
                path = "$base.json",
                jsonBody = buildJsonObject { put("title", title) }.toString(),
            )
        }
    }

    /**
     * クライアントの生成・解放と company_id の解決をまとめる。3 つの操作すべてが必要とするため。
     *
     * 渡すのは拡張子を付ける前のベースパス。一覧と作成は `.json` を、詳細は `/<id>.json` を足す。
     * ここで `.json` まで付けると、詳細取得だけが剥がして付け直すことになる。
     */
    private suspend fun <T> withInventoriesBasePath(
        block: suspend (HttpClient, String) -> T,
    ): T = httpClientFactory().use { client ->
        val companyId = companyIdProvider(client)
        block(client, "/api/v2/orgs/companies/$companyId/inventories")
    }
}
