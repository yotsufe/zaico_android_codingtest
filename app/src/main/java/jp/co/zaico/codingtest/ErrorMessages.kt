package jp.co.zaico.codingtest

import android.content.Context
import androidx.annotation.StringRes

/**
 * 例外を画面に表示できる文言に変換する。
 *
 * 文字列リソースを必要とする分岐をここに集めることで、データ層を Context から切り離している。
 */
fun Context.displayMessageOf(error: Throwable): String = when (error) {
    is ApiException -> apiMessageOf(error)
    else -> error.message ?: error.toString()
}

private fun Context.apiMessageOf(error: ApiException): String {
    if (error is ApiException.ErrorResponse) return error.reason

    val template = checkNotNull(messageResOf(error))
    return when (error) {
        is ApiException.NotObject -> getString(template, getString(labelResOf(error.part)))
        is ApiException.NotArray -> getString(template, getString(labelResOf(error.part)))
        is ApiException.MissingField -> getString(template, error.key)
        is ApiException.NotInteger -> getString(template, error.key)
        is ApiException.OutOfRange -> getString(template, error.key)
        else -> getString(template)
    }
}

/**
 * 例外に対応する文言のリソース。[ApiException.ErrorResponse] だけ null。
 *
 * **`Context` を取らないのは、対応が正しいかを単体テストで固定するため。**
 * `when` の網羅性が保証するのは「分岐があること」だけで、対応が合っていることではない。
 */
@StringRes
internal fun messageResOf(error: ApiException): Int? = when (error) {
    is ApiException.ErrorResponse -> null
    is ApiException.TokenMissing -> R.string.error_api_token_missing
    is ApiException.NotJson -> R.string.error_response_not_json
    is ApiException.MissingData -> R.string.error_response_missing_data
    is ApiException.NotObject -> R.string.error_not_object
    is ApiException.NotArray -> R.string.error_not_array
    is ApiException.MissingField -> R.string.error_missing_field
    is ApiException.NotInteger -> R.string.error_not_integer
    is ApiException.OutOfRange -> R.string.error_out_of_range
    is ApiException.NoCompany -> R.string.error_no_company
}

@StringRes
internal fun labelResOf(part: JsonPart): Int = when (part) {
    JsonPart.RESPONSE -> R.string.json_part_response
    JsonPart.DATA -> R.string.json_part_data
    JsonPart.INVENTORY -> R.string.json_part_inventory
    JsonPart.COMPANY -> R.string.json_part_company
}
