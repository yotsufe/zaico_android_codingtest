package jp.co.zaico.codingtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `例外ごとに対応する文言を返す`() {
        assertEquals(R.string.error_api_token_missing, messageResOf(ApiException.TokenMissing()))
        assertEquals(R.string.error_response_not_json, messageResOf(ApiException.NotJson()))
        assertEquals(R.string.error_response_missing_data, messageResOf(ApiException.MissingData()))
        assertEquals(R.string.error_not_object, messageResOf(ApiException.NotObject(JsonPart.DATA)))
        assertEquals(R.string.error_not_array, messageResOf(ApiException.NotArray(JsonPart.DATA)))
        assertEquals(R.string.error_missing_field, messageResOf(ApiException.MissingField("id")))
        assertEquals(R.string.error_not_integer, messageResOf(ApiException.NotInteger("id")))
        assertEquals(R.string.error_out_of_range, messageResOf(ApiException.OutOfRange("id")))
        assertEquals(R.string.error_no_company, messageResOf(ApiException.NoCompany()))
    }

    @Test
    fun `ErrorResponse は API の文言を使うのでリソースを持たない`() {
        assertNull(messageResOf(ApiException.ErrorResponse("トークンが無効です。")))
    }

    @Test
    fun `レスポンスの部位ごとに対応するラベルを返す`() {
        assertEquals(R.string.json_part_response, labelResOf(JsonPart.RESPONSE))
        assertEquals(R.string.json_part_data, labelResOf(JsonPart.DATA))
        assertEquals(R.string.json_part_inventory, labelResOf(JsonPart.INVENTORY))
        assertEquals(R.string.json_part_company, labelResOf(JsonPart.COMPANY))
    }
}
