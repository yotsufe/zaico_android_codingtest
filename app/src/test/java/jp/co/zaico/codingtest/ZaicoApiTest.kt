package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * ZaicoApi のレスポンス解析に対するテスト。
 *
 * リファクタリングでふるまいを壊していないことを確かめるための安全網として先に用意する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZaicoApiTest {

    private val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "test-token")

    private fun clientOf(engine: MockEngine) = HttpClient(engine)

    @Test
    fun `parseData は data フィールドの中身を返す`() {
        val body = """{"data":[{"id":1,"title":"ねじ","quantity":"10"}]}"""

        assertEquals(1, ZaicoApi.parseData(body).jsonArray.size)
    }

    @Test(expected = ApiException::class)
    fun `parseData は data が含まれていなければ ApiException を投げる`() {
        ZaicoApi.parseData("""{"message":"something went wrong"}""")
    }

    @Test
    fun `toInventory は id と title と quantity を写す`() {
        val json = ZaicoApi.parseData("""{"data":{"id":7,"title":"ねじ","quantity":"10"}}""").jsonObject

        assertEquals(Inventory(id = 7, title = "ねじ", quantity = "10"), ZaicoApi.toInventory(json))
    }

    @Test
    fun `toInventory は title が欠けていたら空文字にする`() {
        val json = ZaicoApi.parseData("""{"data":{"id":7,"quantity":"10"}}""").jsonObject

        assertEquals("", ZaicoApi.toInventory(json).title)
    }

    @Test
    fun `toInventory は quantity が欠けていたら空文字にする`() {
        val json = ZaicoApi.parseData("""{"data":{"id":7,"title":"ねじ"}}""").jsonObject

        assertEquals("", ZaicoApi.toInventory(json).quantity)
    }

    @Test
    fun `getRawBody は baseUrl 末尾と path 先頭のスラッシュを重複させない`() = runTest {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond("""{"data":[]}""")
        }

        ZaicoApi.getRawBody(clientOf(engine), endpoint, "/api/v2/orgs/companies.json")

        assertEquals("https://example.test/api/v2/orgs/companies.json", requestedUrl)
    }

    @Test
    fun `getRawBody は Authorization ヘッダにトークンを付ける`() = runTest {
        var authorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond("""{"data":[]}""")
        }

        ZaicoApi.getRawBody(clientOf(engine), endpoint, "/api/v2/orgs/companies.json")

        assertEquals("Bearer test-token", authorization)
    }

    @Test
    fun `getRawBody はエラーレスポンスの detail を ApiException のメッセージにする`() {
        // v2 のエラーは RFC 7807 Problem Details 形式。実機のレスポンスをそのまま使う。
        val engine = MockEngine {
            respond(
                """{"title":"認証エラー","status":401,"detail":"認証トークンが無効または未指定です。"}""",
                HttpStatusCode.Unauthorized
            )
        }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { ZaicoApi.getRawBody(clientOf(engine), endpoint, "/api/v2/x.json") }
        }

        assertEquals("認証トークンが無効または未指定です。", error.message)
    }

    @Test
    fun `getRawBody は detail が無ければ title を ApiException のメッセージにする`() {
        val engine = MockEngine {
            respond("""{"title":"リクエストエラー","status":400}""", HttpStatusCode.BadRequest)
        }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { ZaicoApi.getRawBody(clientOf(engine), endpoint, "/api/v2/x.json") }
        }

        assertEquals("リクエストエラー", error.message)
    }

    @Test
    fun `getRawBody は解析できない本文ならステータス文字列で ApiException を投げる`() {
        val engine = MockEngine { respond("oops", HttpStatusCode.InternalServerError) }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { ZaicoApi.getRawBody(clientOf(engine), endpoint, "/api/v2/x.json") }
        }

        assertEquals(HttpStatusCode.InternalServerError.toString(), error.message)
    }

    @Test
    fun `getRawBody はトークンが空ならリクエストを送らずに ApiTokenMissingException を投げる`() {
        val engine = MockEngine { respond("") }

        assertThrows(ApiTokenMissingException::class.java) {
            runBlocking {
                ZaicoApi.getRawBody(clientOf(engine), endpoint.copy(token = ""), "/api/v2/x.json")
            }
        }

        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `fetchCompanyId は先頭の会社の id を返す`() = runTest {
        val engine = MockEngine { respond("""{"data":[{"id":42},{"id":99}]}""") }

        assertEquals(42, ZaicoApi.fetchCompanyId(clientOf(engine), endpoint))
    }

    @Test
    fun `fetchCompanyId は会社が 0 件なら ApiException を投げる`() {
        val engine = MockEngine { respond("""{"data":[]}""") }

        val error = assertThrows(ApiException::class.java) {
            runBlocking { ZaicoApi.fetchCompanyId(clientOf(engine), endpoint) }
        }

        assertEquals("利用可能な会社が見つかりませんでした", error.message)
    }
}
