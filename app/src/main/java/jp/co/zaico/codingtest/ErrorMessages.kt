package jp.co.zaico.codingtest

import android.content.Context

/**
 * 例外を画面に表示できる文言に変換する。
 *
 * 文字列リソースを必要とする分岐をここに集めることで、データ層を Context から切り離している。
 */
fun Context.displayMessageOf(error: Throwable): String = when (error) {
    is ApiTokenMissingException -> getString(R.string.error_api_token_missing)
    else -> error.message ?: error.toString()
}
