package jp.co.zaico.codingtest

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class FirstViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    // TODO: 一覧の再読み込み対応時に viewModelScope + StateFlow へ移す
    // データ取得（失敗した場合は Result.failure を返し、呼び出し側でエラー表示する）
    fun getInventories(): Result<List<Inventory>> = runBlocking(Dispatchers.IO) {
        runCatching { repository.getInventories() }
    }

}
