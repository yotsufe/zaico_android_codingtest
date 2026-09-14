package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * 在庫作成リクエストの形を固定するテスト。
 *
 * 公開 API v2 ドキュメントの Inventories_create に準拠していることを、
 * 実際の通信なしに検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZaicoInventoryRepositoryTest {

    private val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "test-token")

    private fun repositoryOf(engine: MockEngine) = ZaicoInventoryRepository(
        endpoint = endpoint,
        companyIdProvider = CompanyIdProvider(endpoint),
        httpClientFactory = { HttpClient(engine) },
    )

    /**
     * company_id の取得だけ先に応答し、残りを [handler] に任せる。
     *
     * CompanyIdProvider を実物のまま通すため、在庫のリクエストの前に会社一覧が 1 回飛ぶ。
     */
    private fun engineOf(companyId: Int = 42, handler: MockRequestHandler) = MockEngine { request ->
        // CompanyIdProvider が叩くパス。変えるとこの分岐が外れ、在庫用の応答が
        // 会社一覧に返って原因の分かりにくい失敗になる。
        if (request.url.encodedPath.endsWith("/orgs/companies.json")) {
            respond("""{"data":[{"id":$companyId}]}""")
        } else {
            handler(this, request)
        }
    }

    @Test
    fun `createInventory は会社 ID を含む v2 のパスに JSON を POST する`() = runTest {
        var request: HttpRequestData? = null
        val engine = engineOf {
            request = it
            // 実機のレスポンス形式（一部抜粋）。作成結果は使わないが、現実の形で固定しておく。
            respond("""{"data":{"id":74107958,"title":"ねじ","quantity":null}}""")
        }

        repositoryOf(engine).createInventory("ねじ")

        val actual = requireNotNull(request)
        assertEquals(HttpMethod.Post, actual.method)
        assertEquals(
            "https://example.test/api/v2/orgs/companies/42/inventories.json",
            actual.url.toString(),
        )
        assertEquals("Bearer test-token", actual.headers[HttpHeaders.Authorization])
        assertEquals("""{"title":"ねじ"}""", (actual.body as TextContent).text)
    }

    @Test
    fun `createInventory は 200 でレスポンス本文が想定外でも成功する`() = runTest {
        val engine = engineOf {
            respond("""{"code":200,"status":"success","data_id":123}""")
        }

        repositoryOf(engine).createInventory("ねじ")
    }

    @Test
    fun `createInventory は 201 でも成功する`() = runTest {
        val engine = engineOf { respond("", HttpStatusCode.Created) }

        repositoryOf(engine).createInventory("ねじ")
    }

    @Test
    fun `createInventory は 400 ならエラーレスポンスの detail を持つ ApiException を投げる`() {
        // 実機で title を省いて POST したときのレスポンス。
        val engine = engineOf {
            respond(
                """{"title":"リクエストエラー","status":400,"detail":"missing required parameters: title"}""",
                HttpStatusCode.BadRequest,
            )
        }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }

        assertEquals("missing required parameters: title", error.message)
    }

    @Test
    fun `createInventory は解析できないエラー本文ならステータスを message にする`() {
        val engine = engineOf { respond("oops", HttpStatusCode.InternalServerError) }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }

        assertEquals(HttpStatusCode.InternalServerError.toString(), error.message)
    }

    @Test
    fun `トークンが空なら company_id の取得も含め一切通信しない`() {
        val engine = engineOf { respond("") }
        val tokenless = endpoint.copy(token = "")
        val repository = ZaicoInventoryRepository(
            endpoint = tokenless,
            companyIdProvider = CompanyIdProvider(tokenless),
            httpClientFactory = { HttpClient(engine) },
        )

        assertThrows(ApiTokenMissingException::class.java) {
            runBlocking { repository.createInventory("ねじ") }
        }

        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `createInventory は通信に失敗したら例外を伝える`() {
        val engine = engineOf { throw IOException("network down") }

        assertThrows(IOException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }
    }

    @Test
    fun `createInventory は会社 ID を 1 回だけ取得してから POST する`() = runTest {
        var companyRequests = 0
        val engine = MockEngine { request ->
            // CompanyIdProvider が叩くパス。変えるとこの分岐が外れ、在庫用の応答が
            // 会社一覧に返って原因の分かりにくい失敗になる。
            if (request.url.encodedPath.endsWith("/orgs/companies.json")) {
                companyRequests++
                respond("""{"data":[{"id":42}]}""")
            } else {
                respond("""{"data":{"id":1}}""")
            }
        }
        val repository = ZaicoInventoryRepository(
            endpoint = endpoint,
            companyIdProvider = CompanyIdProvider(endpoint),
            httpClientFactory = { HttpClient(engine) },
        )

        repository.createInventory("ねじ")
        repository.createInventory("ばね")

        // 2 回作成しても会社一覧は 1 回しか叩かない（CompanyIdProvider が保持する）
        assertEquals(1, companyRequests)
    }

    @Test
    fun `getInventories は一覧エンドポイントから在庫を読み取る`() = runTest {
        var requestedUrl = ""
        val engine = engineOf {
            requestedUrl = it.url.toString()
            respond("""{"data":[{"id":1,"title":"ねじ","quantity":"10"},{"id":2,"title":"ばね"}]}""")
        }

        val inventories = repositoryOf(engine).getInventories()

        assertEquals(
            "https://example.test/api/v2/orgs/companies/42/inventories.json",
            requestedUrl,
        )
        assertEquals(listOf(Inventory(1, "ねじ", "10"), Inventory(2, "ばね", "")), inventories.items)
        assertEquals(0, inventories.skipped)
    }

    @Test
    fun `getInventories は形が想定と違う在庫を読み飛ばし、件数を返す`() = runTest {
        // 1 件でも不正なら全件を失う、という挙動を避ける。ただし黙って減らさない。
        val engine = engineOf {
            respond("""{"data":[{"id":1,"title":"ねじ"},{"title":"id が無い"},"文字列"]}""")
        }

        val inventories = repositoryOf(engine).getInventories()

        assertEquals(listOf(Inventory(1, "ねじ", "")), inventories.items)
        assertEquals(2, inventories.skipped)
    }

    @Test
    fun `getInventories は在庫が 0 件なら空のリストを返す`() = runTest {
        val engine = engineOf { respond("""{"data":[]}""") }

        val inventories = repositoryOf(engine).getInventories()

        assertEquals(emptyList<Inventory>(), inventories.items)
        assertEquals(0, inventories.skipped)
    }

    @Test
    fun `getInventory は在庫 ID を含むパスから 1 件を読み取る`() = runTest {
        var requestedUrl = ""
        val engine = engineOf {
            requestedUrl = it.url.toString()
            respond("""{"data":{"id":7,"title":"ねじ","quantity":"10"}}""")
        }

        val inventory = repositoryOf(engine).getInventory(7)

        assertEquals(
            "https://example.test/api/v2/orgs/companies/42/inventories/7.json",
            requestedUrl,
        )
        assertEquals(Inventory(7, "ねじ", "10"), inventory)
    }

    @Test
    fun `getInventories はエラーステータスなら ApiException を投げる`() {
        val engine = engineOf {
            respond(
                """{"title":"認証エラー","status":401,"detail":"トークンが無効です。"}""",
                HttpStatusCode.Unauthorized,
            )
        }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { repositoryOf(engine).getInventories() }
        }

        assertEquals("トークンが無効です。", error.message)
    }
}
