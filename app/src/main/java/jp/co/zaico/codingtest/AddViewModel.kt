package jp.co.zaico.codingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 在庫データ作成画面の状態。
 *
 * 画面は例外の型を解釈せず、この状態に従って描画するだけでよい。
 */
sealed interface AddUiState {
    /** 入力待ち。 */
    data object Idle : AddUiState

    /** 作成中。二重送信を防ぐため入力を無効化する。 */
    data object Saving : AddUiState

    /** タイトルが未入力。入力欄にエラーを表示する。 */
    data object TitleRequired : AddUiState

    /** 作成に成功した。 */
    data object Completed : AddUiState

    /** 作成に失敗した。 */
    data class Failed(val error: Throwable) : AddUiState
}

/** 在庫データ作成画面の ViewModel。 */
@HiltViewModel
class AddViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddUiState>(AddUiState.Idle)
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()

    /** 在庫データを作成する。結果は [uiState] で通知する。 */
    fun createInventory(title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = AddUiState.TitleRequired
            return
        }

        viewModelScope.launch {
            _uiState.value = AddUiState.Saving
            _uiState.value = try {
                repository.createInventory(trimmedTitle)
                AddUiState.Completed
            } catch (cancellation: CancellationException) {
                // runCatching はキャンセルも捕まえてしまうため使わない。
                // 画面が閉じられた際のキャンセルを「作成失敗」として表示しないように再送出する。
                throw cancellation
            } catch (error: Exception) {
                AddUiState.Failed(error)
            }
        }
    }

    /**
     * 結果の表示が終わったことを通知する。
     *
     * StateFlow は最後の値を保持するため、これを呼ばないと画面回転のたびに
     * 同じ Toast が再表示される。
     */
    fun onResultHandled() {
        _uiState.value = AddUiState.Idle
    }
}
