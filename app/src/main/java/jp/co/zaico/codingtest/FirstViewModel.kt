package jp.co.zaico.codingtest

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class FirstViewModel(
    val context: Context
): ViewModel() {

    private val endpoint = ZaicoApiEndpoint.from(context)

    // データ取得（失敗した場合は Result.failure を返し、呼び出し側でエラー表示する）
    fun getInventories(): Result<List<Inventory>> = runBlocking(Dispatchers.IO) {
        runCatching {
            ZaicoApi.newClient().use { client ->
                val companyId = ZaicoApi.companyId(client, endpoint)
                val body = ZaicoApi.getText(
                    client,
                    endpoint,
                    "/api/v2/orgs/companies/$companyId/inventories.json"
                )

                ZaicoApi.dataOf(body).jsonArray.map { ZaicoApi.toInventory(it.jsonObject) }
            }
        }
    }

}
