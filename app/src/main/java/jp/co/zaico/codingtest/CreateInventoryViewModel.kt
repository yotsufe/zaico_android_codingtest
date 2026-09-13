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
sealed interface CreateInventoryUiState {
    /** 入力待ち。 */
    data object Idle : CreateInventoryUiState

    /** 作成中。二重送信を防ぐため入力を無効化する。 */
    data object Saving : CreateInventoryUiState

    /** タイトルが未入力。入力欄にエラーを表示する。 */
    data object TitleRequired : CreateInventoryUiState

    /** 作成に成功した。 */
    data object Completed : CreateInventoryUiState

    /** 作成に失敗した。 */
    data class Failed(val error: Throwable) : CreateInventoryUiState
}

/** 在庫データ作成画面の ViewModel。 */
@HiltViewModel
class CreateInventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateInventoryUiState>(CreateInventoryUiState.Idle)
    val uiState: StateFlow<CreateInventoryUiState> = _uiState.asStateFlow()

    /** 在庫データを作成する。結果は [uiState] で通知する。 */
    fun createInventory(title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = CreateInventoryUiState.TitleRequired
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateInventoryUiState.Saving
            _uiState.value = try {
                repository.createInventory(trimmedTitle)
                CreateInventoryUiState.Completed
            } catch (cancellation: CancellationException) {
                // runCatching はキャンセルも捕まえてしまうため使わない。
                // 画面が閉じられた際のキャンセルを「作成失敗」として表示しないように再送出する。
                throw cancellation
            } catch (error: Exception) {
                CreateInventoryUiState.Failed(error)
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
        _uiState.value = CreateInventoryUiState.Idle
    }
}
