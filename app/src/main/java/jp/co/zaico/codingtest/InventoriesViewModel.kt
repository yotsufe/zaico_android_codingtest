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

/** 在庫一覧画面の状態。 */
sealed interface InventoriesUiState {

    /** 読み込み中。 */
    data object Loading : InventoriesUiState

    /** 読み込みに成功した。 */
    data class Loaded(val inventories: List<Inventory>) : InventoriesUiState

    /**
     * 読み込みに失敗した。
     *
     * [error] はまだ画面に出していないエラー。表示後に [InventoriesViewModel.onErrorShown] を
     * 呼ぶと null になる。StateFlow は最後の値を保持するため、消さないと購読し直すたびに
     * 同じ Toast が再表示される。
     */
    data class Failed(val error: Throwable?) : InventoriesUiState
}

/** 在庫一覧画面の ViewModel。 */
@HiltViewModel
class InventoriesViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoriesUiState>(InventoriesUiState.Loading)
    val uiState: StateFlow<InventoriesUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    /**
     * まだ読み込めていなければ読み込む。
     *
     * 画面復帰のたびに呼んでよい。読み込み済みなら通信は起きないので、画面回転や
     * 他アプリからの復帰で無駄に API を叩かない。失敗したまま離れて戻った場合は再試行する。
     */
    fun fetchIfNeeded() {
        if (_uiState.value is InventoriesUiState.Loaded) return
        if (fetchJob?.isActive == true) return
        fetch()
    }

    /** 在庫一覧を読み込み直す。在庫を作成した直後など、内容が変わったときに呼ぶ。 */
    fun fetch() {
        // 直前の読み込みを打ち切る。打ち切らないと通信が並走し、遅れて返った
        // 古い一覧が新しい一覧を上書きして、作成した在庫が消えて見える。
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = InventoriesUiState.Loading
            try {
                _uiState.value = InventoriesUiState.Loaded(repository.getInventories())
            } catch (cancellation: CancellationException) {
                // runCatching はキャンセルも捕まえてしまうため使わない。
                // 打ち切りや画面破棄を「読み込み失敗」として表示しないように再送出する。
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = InventoriesUiState.Failed(error)
            }
        }
    }

    /** エラーを表示し終えたことを通知する。同じエラーが再表示されないようにする。 */
    fun onErrorShown() {
        val state = _uiState.value
        if (state is InventoriesUiState.Failed && state.error != null) {
            _uiState.value = InventoriesUiState.Failed(null)
        }
    }

}
