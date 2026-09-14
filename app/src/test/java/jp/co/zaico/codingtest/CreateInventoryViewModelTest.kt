package jp.co.zaico.codingtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateInventoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `初期状態は Idle`() {
        val viewModel = CreateInventoryViewModel(FakeInventoryRepository())

        assertEquals(CreateInventoryUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `空のタイトルなら通信せずに TitleRequired になる`() = runTest {
        val repository = FakeInventoryRepository()
        val viewModel = CreateInventoryViewModel(repository)

        viewModel.createInventory("")

        assertEquals(CreateInventoryUiState.TitleRequired, viewModel.uiState.value)
        assertEquals(emptyList<String>(), repository.createdTitles)
    }

    @Test
    fun `空白だけのタイトルなら通信せずに TitleRequired になる`() = runTest {
        val repository = FakeInventoryRepository()
        val viewModel = CreateInventoryViewModel(repository)

        viewModel.createInventory("   ")

        assertEquals(CreateInventoryUiState.TitleRequired, viewModel.uiState.value)
        assertEquals(emptyList<String>(), repository.createdTitles)
    }

    @Test
    fun `タイトルの前後の空白を取り除いてリポジトリに渡す`() = runTest {
        val repository = FakeInventoryRepository()

        CreateInventoryViewModel(repository).createInventory("  ねじ  ")

        assertEquals(listOf("ねじ"), repository.createdTitles)
    }

    @Test
    fun `作成に成功したら Completed になる`() = runTest {
        val repository = FakeInventoryRepository()
        val viewModel = CreateInventoryViewModel(repository)

        viewModel.createInventory("ねじ")

        assertEquals(CreateInventoryUiState.Completed, viewModel.uiState.value)
        assertEquals(listOf("ねじ"), repository.createdTitles)
    }

    @Test
    fun `作成中は Saving になり、完了すると Completed になる`() = runTest {
        val latch = CompletableDeferred<Unit>()
        val viewModel = CreateInventoryViewModel(FakeInventoryRepository(latch = latch))

        viewModel.createInventory("ねじ")
        assertEquals(CreateInventoryUiState.Saving, viewModel.uiState.value)

        latch.complete(Unit)
        assertEquals(CreateInventoryUiState.Completed, viewModel.uiState.value)
    }

    @Test
    fun `作成に失敗したら例外を持つ Failed になる`() = runTest {
        val viewModel = CreateInventoryViewModel(
            FakeInventoryRepository(failure = ApiException("Title can't be blank")),
        )

        viewModel.createInventory("ねじ")

        val state = viewModel.uiState.value
        assertTrue(state is CreateInventoryUiState.Failed)
        assertEquals("Title can't be blank", (state as CreateInventoryUiState.Failed).error.message)
    }

    @Test
    fun `結果を表示し終えたら Idle に戻る`() = runTest {
        val viewModel = CreateInventoryViewModel(FakeInventoryRepository(failure = ApiException("error")))
        viewModel.createInventory("ねじ")

        viewModel.onResultHandled()

        assertEquals(CreateInventoryUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `作成がキャンセルされたら Failed にしない`() = runTest {
        // runCatching は CancellationException も捕まえてしまうため、
        // キャンセルが「作成失敗」として画面に出ないことを保証する。
        val viewModel = CreateInventoryViewModel(
            FakeInventoryRepository(failure = CancellationException("cancelled")),
        )

        viewModel.createInventory("ねじ")

        assertEquals(CreateInventoryUiState.Saving, viewModel.uiState.value)
    }
}
