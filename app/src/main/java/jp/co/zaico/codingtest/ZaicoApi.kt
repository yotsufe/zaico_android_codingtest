package jp.co.zaico.codingtest

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** API がエラーステータスを返したときに投げる例外。 */
class ApiException(message: String) : Exception(message)

/**
 * zaico 公開 API v2 へのアクセスをまとめたヘルパー。
 *
 * v1 は組織ユーザートークンを受け付けず 403 を返すため v2 を利用する。
 * v2 の在庫エンドポイントは company_id を必要とするので、初回アクセス時に API から取得して保持する。
 */
object ZaicoApi {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedCompanyId: Int? = null

    fun newClient(): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** 認証ヘッダ付きで GET する。エラーステータスなら ApiException を投げる。 */
    suspend fun getText(context: Context, client: HttpClient, path: String): String {
        val token = context.getString(R.string.api_token)

        val response: HttpResponse = client.get(context.getString(R.string.api_endpoint) + path) {
            header("Authorization", "Bearer $token")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException(errorMessageOf(body) ?: response.status.toString())
        }
        return body
    }

    /** 在庫エンドポイントのパスに必要な company_id を返す（初回のみ API を呼ぶ）。 */
    suspend fun companyId(context: Context, client: HttpClient): Int {
        cachedCompanyId?.let { return it }

        val body = getText(context, client, "/api/v2/orgs/companies.json")
        val companies = dataOf(body).jsonArray
        if (companies.isEmpty()) {
            throw ApiException("利用可能な会社が見つかりませんでした")
        }

        return companies.first().jsonObject["id"]!!.jsonPrimitive.int
            .also { cachedCompanyId = it }
    }

    /** v2 のレスポンスは {"data": ...} で包まれているので、その中身を取り出す。 */
    fun dataOf(body: String): JsonElement =
        json.parseToJsonElement(body).jsonObject["data"]
            ?: throw ApiException("レスポンスに data が含まれていません")

    fun toInventory(json: JsonObject) = Inventory(
        id = json["id"]!!.jsonPrimitive.int,
        title = json["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        quantity = json["quantity"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )

    /** エラーレスポンス {"message": "..."} からメッセージを取り出す。取り出せなければ null。 */
    private fun errorMessageOf(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

}
