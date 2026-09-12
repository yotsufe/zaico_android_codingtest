package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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

    private fun repositoryOf(engine: MockEngine, companyId: Int = 42) =
        ZaicoInventoryRepository(
            endpoint = endpoint,
            httpClientFactory = { HttpClient(engine) },
            companyIdProvider = { companyId },
        )

    @Test
    fun `createInventory は会社 ID を含む v2 のパスに JSON を POST する`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine {
            request = it
            // 実機のレスポンス形式（一部抜粋）。作成結果は使わないが、現実の形で固定しておく。
            respond("""{"data":{"id":74107958,"title":"ねじ","quantity":null}}""")
        }

        repositoryOf(engine).createInventory("ねじ")

        val actual = requireNotNull(request)
        assertEquals(HttpMethod.Post, actual.method)
        assertEquals(
            "https://example.test/api/v2/orgs/companies/42/inventories.json",
            actual.url.toString()
        )
        assertEquals("Bearer test-token", actual.headers[HttpHeaders.Authorization])
        assertEquals("""{"title":"ねじ"}""", (actual.body as TextContent).text)
    }

    @Test
    fun `createInventory は 200 でレスポンス本文が想定外でも成功する`() = runTest {
        val engine = MockEngine {
            respond("""{"code":200,"status":"success","data_id":123}""")
        }

        repositoryOf(engine).createInventory("ねじ")
    }

    @Test
    fun `createInventory は 201 でも成功する`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Created) }

        repositoryOf(engine).createInventory("ねじ")
    }

    @Test
    fun `createInventory は 400 ならエラーレスポンスの detail を持つ ApiException を投げる`() {
        // 実機で title を省いて POST したときのレスポンス。
        val engine = MockEngine {
            respond(
                """{"title":"リクエストエラー","status":400,"detail":"missing required parameters: title"}""",
                HttpStatusCode.BadRequest
            )
        }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }

        assertEquals("missing required parameters: title", error.message)
    }

    @Test
    fun `createInventory は解析できないエラー本文ならステータスを message にする`() {
        val engine = MockEngine { respond("oops", HttpStatusCode.InternalServerError) }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }

        assertEquals(HttpStatusCode.InternalServerError.toString(), error.message)
    }

    @Test
    fun `createInventory はトークンが空ならリクエストを送らずに ApiTokenMissingException を投げる`() {
        val engine = MockEngine { respond("") }
        val repository = ZaicoInventoryRepository(
            endpoint = endpoint.copy(token = ""),
            httpClientFactory = { HttpClient(engine) },
            companyIdProvider = { 42 },
        )

        assertThrows(ApiTokenMissingException::class.java) {
            runBlocking { repository.createInventory("ねじ") }
        }

        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `createInventory は通信に失敗したら例外を伝える`() {
        val engine = MockEngine { throw IOException("network down") }

        assertThrows(IOException::class.java) {
            runBlocking { repositoryOf(engine).createInventory("ねじ") }
        }
    }

    @Test
    fun `createInventory は会社 ID を 1 回だけ取得してから POST する`() = runTest {
        var companyIdCalls = 0
        val engine = MockEngine { respond("""{"data":{"id":1}}""") }
        val repository = ZaicoInventoryRepository(
            endpoint = endpoint,
            httpClientFactory = { HttpClient(engine) },
            companyIdProvider = { companyIdCalls++; 42 },
        )

        repository.createInventory("ねじ")

        assertEquals(1, companyIdCalls)
        assertEquals(1, engine.requestHistory.size)
    }
}
