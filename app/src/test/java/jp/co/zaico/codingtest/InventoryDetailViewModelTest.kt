package jp.co.zaico.codingtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = Inventory(7, "ねじ", "10")

    @Test
    fun `読み込み前は Loading を示す`() {
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(inventory = inventory))

        assertEquals(InventoryDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `読み込みに成功したら指定した在庫の Loaded になる`() = runTest {
        val repository = FakeInventoryRepository(inventory = inventory)
        val viewModel = InventoryDetailViewModel(repository)

        viewModel.fetchIfNeeded(7)

        assertEquals(InventoryDetailUiState.Loaded(inventory), viewModel.uiState.value)
        assertEquals(listOf(7), repository.requestedInventoryIds)
    }

    @Test
    fun `画像 URL があれば画像を表示できる`() = runTest {
        val withImage = inventory.copy(imageUrl = "https://example.test/a.png")
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(inventory = withImage))

        viewModel.fetchIfNeeded(withImage.id)

        val state = viewModel.uiState.value as InventoryDetailUiState.Loaded
        assertTrue(state.canShowImage)
    }

    @Test
    fun `画像 URL が無ければ画像を表示できない`() = runTest {
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(inventory = inventory))

        viewModel.fetchIfNeeded(inventory.id)

        val state = viewModel.uiState.value as InventoryDetailUiState.Loaded
        assertFalse(state.canShowImage)
    }

    @Test
    fun `読み込み中は Loading のままで、完了すると Loaded になる`() = runTest {
        val latch = CompletableDeferred<Unit>()
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(inventory = inventory, latch = latch))

        viewModel.fetchIfNeeded(7)
        assertEquals(InventoryDetailUiState.Loading, viewModel.uiState.value)

        latch.complete(Unit)
        assertEquals(InventoryDetailUiState.Loaded(inventory), viewModel.uiState.value)
    }

    @Test
    fun `読み込みに失敗したら例外を持つ Failed になる`() = runTest {
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(failure = ApiException.ErrorResponse("Not Found")))

        viewModel.fetchIfNeeded(7)

        val state = viewModel.uiState.value
        assertTrue(state is InventoryDetailUiState.Failed)
        assertEquals("Not Found", (state as InventoryDetailUiState.Failed).error?.message)
    }

    @Test
    fun `読み込みがキャンセルされたら Failed にしない`() = runTest {
        // runCatching は CancellationException も捕まえてしまうため、
        // キャンセルが「読み込み失敗」として画面に出ないことを保証する。
        val viewModel = InventoryDetailViewModel(
            FakeInventoryRepository(failure = CancellationException("cancelled")),
        )

        viewModel.fetchIfNeeded(7)

        assertEquals(InventoryDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `fetchIfNeeded は読み込み済みなら通信しない`() = runTest {
        val repository = FakeInventoryRepository(inventory = inventory)
        val viewModel = InventoryDetailViewModel(repository)

        viewModel.fetchIfNeeded(7)
        viewModel.fetchIfNeeded(7)

        assertEquals(listOf(7), repository.requestedInventoryIds)
    }

    @Test
    fun `fetchIfNeeded は読み込み中なら重ねて通信しない`() = runTest {
        // 読み込みが並走しないことは、このガードだけで保証している。
        val latch = CompletableDeferred<Unit>()
        val repository = FakeInventoryRepository(inventory = inventory, latch = latch)
        val viewModel = InventoryDetailViewModel(repository)

        viewModel.fetchIfNeeded(7)
        viewModel.fetchIfNeeded(7)

        assertEquals(listOf(7), repository.requestedInventoryIds)
        latch.complete(Unit)
    }

    @Test
    fun `fetchIfNeeded は失敗したあとなら読み込み直す`() = runTest {
        val repository = FakeInventoryRepository(failure = ApiException.ErrorResponse("Not Found"))
        val viewModel = InventoryDetailViewModel(repository)

        viewModel.fetchIfNeeded(7)
        viewModel.fetchIfNeeded(7)

        assertEquals(listOf(7, 7), repository.requestedInventoryIds)
    }

    @Test
    fun `onErrorShown を呼ぶと同じエラーを二度通知しない`() = runTest {
        val viewModel = InventoryDetailViewModel(FakeInventoryRepository(failure = ApiException.ErrorResponse("Not Found")))

        viewModel.fetchIfNeeded(7)
        viewModel.onErrorShown()

        assertEquals(InventoryDetailUiState.Failed(null), viewModel.uiState.value)
    }
}
