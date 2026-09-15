package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `parseDataAsArray は data の配列を返す`() {
        val body = """{"data":[{"id":1,"title":"ねじ","quantity":"10"}]}"""

        assertEquals(1, ZaicoApi.parseDataAsArray(body).size)
    }

    @Test
    fun `toInventory は id と title と quantity を写す`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"title":"ねじ","quantity":"10"}}""")

        assertEquals(Inventory(id = 7, title = "ねじ", quantity = "10"), ZaicoApi.toInventory(json))
    }

    @Test
    fun `toInventory は title が欠けていたら空文字にする`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"quantity":"10"}}""")

        assertEquals("", ZaicoApi.toInventory(json).title)
    }

    @Test
    fun `toInventory は quantity が欠けていたら空文字にする`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"title":"ねじ"}}""")

        assertEquals("", ZaicoApi.toInventory(json).quantity)
    }

    @Test
    fun `JSON として解釈できない本文なら ApiException を投げる`() {
        val error = assertThrows(ApiException::class.java) { ZaicoApi.parseDataAsArray("not json") }

        assertEquals("レスポンスを JSON として解釈できませんでした", error.message)
    }

    @Test
    fun `本文がオブジェクトでなければ ApiException を投げる`() {
        val error = assertThrows(ApiException::class.java) { ZaicoApi.parseDataAsArray("[1,2,3]") }

        assertEquals("レスポンスがオブジェクトではありません", error.message)
    }

    @Test
    fun `parseDataAsArray は data が配列でなければ ApiException を投げる`() {
        val error = assertThrows(ApiException::class.java) {
            ZaicoApi.parseDataAsArray("""{"data":{"id":1}}""")
        }

        assertEquals("dataが配列ではありません", error.message)
    }

    @Test
    fun `parseDataAsObject は data がオブジェクトでなければ ApiException を投げる`() {
        val error = assertThrows(ApiException::class.java) {
            ZaicoApi.parseDataAsObject("""{"data":[1,2]}""")
        }

        assertEquals("dataがオブジェクトではありません", error.message)
    }

    @Test
    fun `toInventory は item_image の url を写す`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"item_image":{"url":"https://example.test/a.png"}}}""")

        assertEquals("https://example.test/a.png", ZaicoApi.toInventory(json).imageUrl)
    }

    @Test
    fun `toInventory は item_image の url が null なら画像なしにする`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"item_image":{"url":null}}}""")

        assertNull(ZaicoApi.toInventory(json).imageUrl)
    }

    @Test
    fun `toInventory は item_image の url が空白だけなら画像なしにする`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"item_image":{"url":"   "}}}""")

        assertNull(ZaicoApi.toInventory(json).imageUrl)
    }

    @Test
    fun `toInventory は item_image が欠けていても読み取れる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"title":"ねじ"}}""")

        val inventory = ZaicoApi.toInventory(json)

        assertEquals(7, inventory.id)
        assertNull(inventory.imageUrl)
    }

    @Test
    fun `toInventory は item_image がオブジェクトでなくても読み取れる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":7,"title":"ねじ","item_image":"壊れている"}}""")

        val inventory = ZaicoApi.toInventory(json)

        assertEquals("ねじ", inventory.title)
        assertNull(inventory.imageUrl)
    }

    @Test
    fun `toInventory は id が欠けていたら ApiException を投げる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"title":"ねじ"}}""")

        val error = assertThrows(ApiException::class.java) { ZaicoApi.toInventory(json) }

        assertEquals("idが含まれていません", error.message)
    }

    @Test
    fun `toInventory は id が整数でなければ ApiException を投げる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":"abc"}}""")

        val error = assertThrows(ApiException::class.java) { ZaicoApi.toInventory(json) }

        assertEquals("idが整数ではありません", error.message)
    }

    @Test
    fun `toInventory は在庫がオブジェクトでなければ ApiException を投げる`() {
        val element = ZaicoApi.parseDataAsArray("""{"data":[1]}""").first()

        val error = assertThrows(ApiException::class.java) { ZaicoApi.toInventory(element) }

        assertEquals("在庫がオブジェクトではありません", error.message)
    }

    @Test
    fun `toInventory は title が JSON の null でも空文字にする`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":1,"title":null}}""")

        assertEquals("", ZaicoApi.toInventory(json).title)
    }

    @Test
    fun `data が含まれていなければ ApiException を投げる`() {
        val error = assertThrows(ApiException::class.java) {
            ZaicoApi.parseDataAsArray("""{"message":"something went wrong"}""")
        }

        assertEquals("レスポンスに data が含まれていません", error.message)
    }

    @Test
    fun `toInventory は id が Int の範囲を超えていたら範囲と分かる ApiException を投げる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":12345678901}}""")

        val error = assertThrows(ApiException::class.java) { ZaicoApi.toInventory(json) }

        assertEquals("idが扱える範囲を超えています", error.message)
    }

    @Test
    fun `toInventory は id が文字列の数値でも受け入れる`() {
        val json = ZaicoApi.parseDataAsObject("""{"data":{"id":"7"}}""")

        assertEquals(7, ZaicoApi.toInventory(json).id)
    }

    @Test
    fun `getRawBody は endpoint が組み立てた URL にリクエストする`() = runTest {
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
                HttpStatusCode.Unauthorized,
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
}
