package jp.co.zaico.codingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 在庫詳細画面の状態。 */
sealed interface InventoryDetailUiState {

    /** 読み込み中。 */
    data object Loading : InventoryDetailUiState

    /** 読み込みに成功した。 */
    data class Loaded(val inventory: Inventory) : InventoryDetailUiState

    /**
     * 読み込みに失敗した。
     *
     * [error] はまだ画面に出していないエラー。表示後に [InventoryDetailViewModel.onErrorShown] を
     * 呼ぶと null になる。StateFlow は最後の値を保持するため、消さないと購読し直すたびに
     * 同じ Toast が再表示される。
     */
    data class Failed(val error: Throwable?) : InventoryDetailUiState
}

/** 在庫詳細画面の ViewModel。 */
@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryDetailUiState>(InventoryDetailUiState.Loading)
    val uiState: StateFlow<InventoryDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * まだ読み込めていなければ読み込む。
     *
     * この ViewModel は詳細画面 1 つに紐づき、表示する在庫は途中で変わらないため、
     * [inventoryId] の変化は考慮しなくてよい。画面回転では読み込み直さない。
     */
    fun loadIfNeeded(inventoryId: Int) {
        if (_uiState.value is InventoryDetailUiState.Loaded) return
        if (loadJob?.isActive == true) return
        load(inventoryId)
    }

    /**
     * 指定された在庫データを読み込み直す。
     *
     * 呼び口は [loadIfNeeded] だけなので実際には並走しないが、呼び出し側のガードに
     * 依存せず「常に直前を打ち切ってから読む」という性質をこの関数だけで満たしておく。
     */
    private fun load(inventoryId: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = InventoryDetailUiState.Loading
            try {
                _uiState.value = InventoryDetailUiState.Loaded(repository.getInventory(inventoryId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = InventoryDetailUiState.Failed(error)
            }
        }
    }

    /** エラーを表示し終えたことを通知する。同じエラーが再表示されないようにする。 */
    fun onErrorShown() {
        val state = _uiState.value
        if (state is InventoryDetailUiState.Failed && state.error != null) {
            _uiState.value = InventoryDetailUiState.Failed(null)
        }
    }

}
