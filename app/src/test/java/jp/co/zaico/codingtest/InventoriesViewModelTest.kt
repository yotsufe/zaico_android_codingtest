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
class InventoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventories = listOf(Inventory(1, "ねじ", "10"), Inventory(2, "ばね", "5"))

    @Test
    fun `読み込み前は Loading を示す`() {
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories))

        assertEquals(InventoriesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `読み込みに成功したら Loaded になる`() = runTest {
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories))

        viewModel.fetch()

        assertEquals(InventoriesUiState.Loaded(inventories), viewModel.uiState.value)
    }

    @Test
    fun `読み飛ばした件数を Loaded に持たせる`() = runTest {
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories, skipped = 2))

        viewModel.fetch()

        assertEquals(InventoriesUiState.Loaded(inventories, skipped = 2), viewModel.uiState.value)
    }

    @Test
    fun `在庫が 0 件なら isEmpty になる`() = runTest {
        val viewModel = InventoriesViewModel(FakeInventoryRepository(emptyList()))

        viewModel.fetch()

        val state = viewModel.uiState.value as InventoriesUiState.Loaded
        assertTrue(state.isEmpty)
        assertFalse(state.hasUnshownSkipped)
    }

    @Test
    fun `onSkippedShown を呼ぶと同じ読み飛ばしを二度通知しない`() = runTest {
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories, skipped = 2))

        viewModel.fetch()
        assertTrue((viewModel.uiState.value as InventoriesUiState.Loaded).hasUnshownSkipped)

        viewModel.onSkippedShown()

        val state = viewModel.uiState.value as InventoriesUiState.Loaded
        assertFalse(state.hasUnshownSkipped)
        assertEquals(inventories, state.inventories)
    }

    @Test
    fun `読み込み中は Loading のままで、完了すると Loaded になる`() = runTest {
        val latch = CompletableDeferred<Unit>()
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories, latch = latch))

        viewModel.fetch()
        assertEquals(InventoriesUiState.Loading, viewModel.uiState.value)

        latch.complete(Unit)
        assertEquals(InventoriesUiState.Loaded(inventories), viewModel.uiState.value)
    }

    @Test
    fun `読み込みに失敗したら例外を持つ Failed になる`() = runTest {
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = ApiException.ErrorResponse("トークンが無効です。")),
        )

        viewModel.fetch()

        val state = viewModel.uiState.value
        assertTrue(state is InventoriesUiState.Failed)
        assertEquals("トークンが無効です。", (state as InventoriesUiState.Failed).error?.message)
    }

    @Test
    fun `fetch を呼ぶたびに読み込み直す`() = runTest {
        val repository = FakeInventoryRepository(inventories)
        val viewModel = InventoriesViewModel(repository)

        viewModel.fetch()
        viewModel.fetch()

        assertEquals(2, repository.getInventoriesCallCount)
    }

    @Test
    fun `読み込みがキャンセルされたら Failed にしない`() = runTest {
        // runCatching は CancellationException も捕まえてしまうため、
        // キャンセルが「読み込み失敗」として画面に出ないことを保証する。
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = CancellationException("cancelled")),
        )

        viewModel.fetch()

        assertEquals(InventoriesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `fetch が並走しても後から呼んだ方の結果が残る`() = runTest {
        // 打ち切らずに並走させると、遅れて返った古い一覧が新しい一覧を上書きし、
        // 作成したばかりの在庫が消えて見える。
        val repository = SlowRepository()
        val viewModel = InventoriesViewModel(repository)

        viewModel.fetch() // 1 本目（古い）
        viewModel.fetch() // 2 本目（新しい）

        val stale = listOf(Inventory(1, "古い一覧", "1"))
        val fresh = listOf(Inventory(2, "作成した在庫", "1"))

        repository.pendingResponses[1].complete(fresh) // 新しい方が先に返る
        repository.pendingResponses[0].complete(stale) // 古い方が後に返る

        assertEquals(InventoriesUiState.Loaded(fresh), viewModel.uiState.value)
    }

    @Test
    fun `fetchIfNeeded は読み込み済みなら通信しない`() = runTest {
        val repository = FakeInventoryRepository(inventories)
        val viewModel = InventoriesViewModel(repository)

        viewModel.fetch()
        viewModel.fetchIfNeeded()

        assertEquals(1, repository.getInventoriesCallCount)
    }

    @Test
    fun `fetchIfNeeded は読み込み中なら重ねて通信しない`() = runTest {
        val latch = CompletableDeferred<Unit>()
        val repository = FakeInventoryRepository(inventories, latch = latch)
        val viewModel = InventoriesViewModel(repository)

        viewModel.fetchIfNeeded()
        viewModel.fetchIfNeeded()

        assertEquals(1, repository.getInventoriesCallCount)
        latch.complete(Unit)
    }

    @Test
    fun `fetchIfNeeded は失敗したあとなら読み込み直す`() = runTest {
        val repository = FakeInventoryRepository(failure = ApiException.ErrorResponse("圏外です。"))
        val viewModel = InventoriesViewModel(repository)

        viewModel.fetchIfNeeded()
        viewModel.fetchIfNeeded()

        assertEquals(2, repository.getInventoriesCallCount)
    }

    @Test
    fun `onErrorShown を呼ぶと同じエラーを二度通知しない`() = runTest {
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = ApiException.ErrorResponse("圏外です。")),
        )

        viewModel.fetch()
        viewModel.onErrorShown()

        assertEquals(InventoriesUiState.Failed(null), viewModel.uiState.value)
    }

    /**
     * 呼ばれるたびに応答を保留し、どの順で返すかをテストから操作できるようにする。
     *
     * latch と違い値を運ぶ（complete に一覧を渡す）ので、名前も応答であることを示す。
     */
    private class SlowRepository : InventoryRepository {
        val pendingResponses = mutableListOf<CompletableDeferred<List<Inventory>>>()

        override suspend fun getInventories(): Inventories {
            val response = CompletableDeferred<List<Inventory>>()
            pendingResponses += response
            return Inventories(items = response.await(), skipped = 0)
        }

        override suspend fun getInventory(inventoryId: Int): Inventory = error("未使用")
        override suspend fun createInventory(title: String) = error("未使用")
    }
}
