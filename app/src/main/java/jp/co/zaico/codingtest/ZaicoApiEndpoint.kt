package jp.co.zaico.codingtest

import android.content.Context

/**
 * zaico API の接続先。
 *
 * パスから完全な URL を組み立て、認証ヘッダを提供する。
 * Context と BuildConfig への依存をこのクラス 1 箇所に閉じ込めることで、
 * データ層を JVM 上のユニットテストから呼べるようにしている。
 */
data class ZaicoApiEndpoint(
    val baseUrl: String,
    val token: String,
) {

    /**
     * ベース URL とパスを連結する。
     *
     * 双方のスラッシュが重なって `https://example.test//api/...` にならないようにする。
     */
    fun urlOf(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** Authorization ヘッダに設定する値。認証方式の知識をここに集約する。 */
    val authorizationHeader: String get() = "Bearer $token"

    /** API トークンが設定されているか。 */
    val hasToken: Boolean get() = token.isNotEmpty()

    companion object {
        fun from(context: Context) = ZaicoApiEndpoint(
            baseUrl = context.getString(R.string.api_endpoint),
            token = BuildConfig.ZAICO_API_TOKEN,
        )
    }
}
