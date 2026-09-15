package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * API へのアクセスが失敗したことを表す。
 *
 * **表示する文言は持たない。** UI 層が [displayMessageOf] で決める。
 * 唯一の例外が [ErrorResponse] で、API 自身の文言をそのまま出す。
 */
sealed class ApiException(message: String) : Exception(message) {

    class TokenMissing : ApiException("API token is not configured")

    /** [reason] は API 自身が返した文言。こちらで作らない。 */
    class ErrorResponse(val reason: String) : ApiException(reason)

    class NotJson : ApiException("response is not JSON")

    class MissingData : ApiException("response has no data")

    class NotObject(val part: JsonPart) : ApiException("$part is not an object")

    class NotArray(val part: JsonPart) : ApiException("$part is not an array")

    class MissingField(val key: String) : ApiException("$key is missing")

    class NotInteger(val key: String) : ApiException("$key is not an integer")

    class OutOfRange(val key: String) : ApiException("$key is out of range")

    class NoCompany : ApiException("no company available")
}

/**
 * レスポンスのどの部分かを表す。表示名は UI 層が持つ。
 */
enum class JsonPart { RESPONSE, DATA, INVENTORY, COMPANY }

/**
 * zaico 公開 API v2 へのアクセスをまとめたヘルパー。
 *
 * v1 は組織ユーザートークンを受け付けず 403 を返すため v2 を利用する。
 * v2 の在庫エンドポイントが必要とする company_id の解決は [CompanyIdProvider] が持つ。
 */
object ZaicoApi {

    private val json = Json { ignoreUnknownKeys = true }

    fun newClient(): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** 認証ヘッダ付きで GET する。エラーステータスなら ApiException を投げる。 */
    suspend fun getRawBody(
        client: HttpClient,
        endpoint: ZaicoApiEndpoint,
        path: String,
    ): String = request(endpoint, path) { url ->
        client.get(url) { authorize(endpoint) }
    }

    /** 認証ヘッダ付きで JSON を POST する。エラーステータスなら ApiException を投げる。 */
    suspend fun postRawBody(
        client: HttpClient,
        endpoint: ZaicoApiEndpoint,
        path: String,
        jsonBody: String,
    ): String = request(endpoint, path) { url ->
        client.post(url) {
            authorize(endpoint)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
    }

    /**
     * トークンの有無を確かめてから [send] を実行し、レスポンス本文を返す。
     *
     * トークンが空のときはリクエストを送らない。エラーステータスなら ApiException を投げる。
     */
    private suspend fun request(
        endpoint: ZaicoApiEndpoint,
        path: String,
        send: suspend (String) -> HttpResponse,
    ): String {
        if (!endpoint.hasToken) {
            throw ApiException.TokenMissing()
        }

        val response = send(endpoint.urlOf(path))
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException.ErrorResponse(errorMessageOf(body) ?: response.status.toString())
        }
        return body
    }

    private fun HttpRequestBuilder.authorize(endpoint: ZaicoApiEndpoint) {
        header("Authorization", endpoint.authorizationHeader)
    }

    /** オブジェクトの必須な整数フィールドを取り出す。[part] は失敗時にどこを指すかを表す。 */
    fun requireIntOf(element: JsonElement, part: JsonPart, key: String): Int = element.asObject(part).requireInt(key)

    /** v2 のレスポンスは {"data": ...} で包まれているので、その中身を取り出す。 */
    private fun parseData(body: String): JsonElement {
        val root = runCatching { json.parseToJsonElement(body) }
            .getOrElse { throw ApiException.NotJson() }
        return root.asObject(JsonPart.RESPONSE)["data"]
            ?: throw ApiException.MissingData()
    }

    /** data が配列であることまで確かめて取り出す。 */
    fun parseDataAsArray(body: String): JsonArray = parseData(body).asArray(JsonPart.DATA)

    /** data がオブジェクトであることまで確かめて取り出す。 */
    fun parseDataAsObject(body: String): JsonObject = parseData(body).asObject(JsonPart.DATA)

    /** 在庫 1 件を表す JSON を Inventory に写す。オブジェクトでなければ ApiException。 */
    fun toInventory(element: JsonElement): Inventory {
        val json = element.asObject(JsonPart.INVENTORY)
        return Inventory(
            id = json.requireInt("id"),
            title = json.stringOrEmpty("title"),
            quantity = json.stringOrEmpty("quantity"),
            // 形が違っても例外にしない。例外にすると、画像の不備で在庫そのものが
            // 一覧から読み飛ばされてしまう。
            imageUrl = (json["item_image"] as? JsonObject)?.nonBlank("url"),
        )
    }

    // --- JSON の形の検証 ---------------------------------------------------
    // 想定と違う形が来たとき、kotlinx.serialization の例外（「Element ... is not a
    // JsonPrimitive」など）がそのまま画面に出ていた。ApiException に変換して、
    // どこが想定と違うのかが分かる文言にする。

    /** 整数として読めるかの判定。範囲超過と「そもそも整数でない」を区別するために使う。 */
    private val integerText = Regex("-?\\d+")

    private fun parseOrNull(body: String): JsonElement? = try {
        json.parseToJsonElement(body)
    } catch (e: SerializationException) {
        null
    }

    private fun JsonElement.asObject(part: JsonPart): JsonObject = this as? JsonObject ?: throw ApiException.NotObject(part)

    private fun JsonElement.asArray(part: JsonPart): JsonArray = this as? JsonArray ?: throw ApiException.NotArray(part)

    /**
     * 必須の整数フィールド。欠落・型違い・数値でない文字列のいずれも ApiException にする。
     *
     * `intOrNull` ではなく生の文字列から変換しているのは、Int の範囲を超えた値を
     * 「整数ではない」と誤って伝えないため。範囲超過は別の文言にする。
     * quantity が文字列で返る API なので、id も "7" のような文字列で来る場合を受け入れる。
     */
    private fun JsonObject.requireInt(key: String): Int {
        val value = this[key] ?: throw ApiException.MissingField(key)
        val text = (value as? JsonPrimitive)?.contentOrNull
            ?: throw ApiException.NotInteger(key)
        return text.toIntOrNull() ?: if (integerText.matches(text)) {
            // 整数の形はしているが Int に収まらない。「整数ではない」とは区別して伝える。
            throw ApiException.OutOfRange(key)
        } else {
            throw ApiException.NotInteger(key)
        }
    }

    /** 任意の文字列フィールド。欠落・null・型違いはいずれも空文字にする。 */
    private fun JsonObject.stringOrEmpty(key: String): String = stringOrNull(key).orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** 空文字は「無い」として扱う。そのまま返すとステータス表示へのフォールバックが働かない。 */
    private fun JsonObject.nonBlank(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }

    /**
     * エラーレスポンスから表示できるメッセージを取り出す。取り出せなければ null。
     *
     * v2 のエラーは RFC 7807 Problem Details 形式で、
     * {"title": "認証エラー", "status": 401, "detail": "認証トークンが無効または未指定です。"}
     * のように返る。detail のほうが具体的なので優先し、無ければ title を使う。
     */
    private fun errorMessageOf(body: String): String? {
        val error = parseOrNull(body) as? JsonObject ?: return null
        return error.nonBlank("detail") ?: error.nonBlank("title")
    }
}
