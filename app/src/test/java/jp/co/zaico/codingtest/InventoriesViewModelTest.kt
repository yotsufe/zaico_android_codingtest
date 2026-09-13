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

        viewModel.load()

        assertEquals(InventoriesUiState.Loaded(inventories), viewModel.uiState.value)
    }

    @Test
    fun `読み込み中は Loading のままで、完了すると Loaded になる`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = InventoriesViewModel(FakeInventoryRepository(inventories, gate = gate))

        viewModel.load()
        assertEquals(InventoriesUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        assertEquals(InventoriesUiState.Loaded(inventories), viewModel.uiState.value)
    }

    @Test
    fun `読み込みに失敗したら例外を持つ Failed になる`() = runTest {
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = ApiException("トークンが無効です。"))
        )

        viewModel.load()

        val state = viewModel.uiState.value
        assertTrue(state is InventoriesUiState.Failed)
        assertEquals("トークンが無効です。", (state as InventoriesUiState.Failed).error?.message)
    }

    @Test
    fun `load を呼ぶたびに読み込み直す`() = runTest {
        val repository = FakeInventoryRepository(inventories)
        val viewModel = InventoriesViewModel(repository)

        viewModel.load()
        viewModel.load()

        assertEquals(2, repository.getInventoriesCallCount)
    }

    @Test
    fun `読み込みがキャンセルされたら Failed にしない`() = runTest {
        // runCatching は CancellationException も捕まえてしまうため、
        // キャンセルが「読み込み失敗」として画面に出ないことを保証する。
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = CancellationException("cancelled"))
        )

        viewModel.load()

        assertEquals(InventoriesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load が並走しても後から呼んだ方の結果が残る`() = runTest {
        // 打ち切らずに並走させると、遅れて返った古い一覧が新しい一覧を上書きし、
        // 作成したばかりの在庫が消えて見える。
        val repository = SlowRepository()
        val viewModel = InventoriesViewModel(repository)

        viewModel.load()   // 1 本目（古い）
        viewModel.load()   // 2 本目（新しい）

        val stale = listOf(Inventory(1, "古い一覧", "1"))
        val fresh = listOf(Inventory(2, "作成した在庫", "1"))

        repository.gates[1].complete(fresh)   // 新しい方が先に返る
        repository.gates[0].complete(stale)   // 古い方が後に返る

        assertEquals(InventoriesUiState.Loaded(fresh), viewModel.uiState.value)
    }

    @Test
    fun `loadIfNeeded は読み込み済みなら通信しない`() = runTest {
        val repository = FakeInventoryRepository(inventories)
        val viewModel = InventoriesViewModel(repository)

        viewModel.load()
        viewModel.loadIfNeeded()

        assertEquals(1, repository.getInventoriesCallCount)
    }

    @Test
    fun `loadIfNeeded は読み込み中なら重ねて通信しない`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeInventoryRepository(inventories, gate = gate)
        val viewModel = InventoriesViewModel(repository)

        viewModel.loadIfNeeded()
        viewModel.loadIfNeeded()

        assertEquals(1, repository.getInventoriesCallCount)
        gate.complete(Unit)
    }

    @Test
    fun `loadIfNeeded は失敗したあとなら読み込み直す`() = runTest {
        val repository = FakeInventoryRepository(failure = ApiException("圏外です。"))
        val viewModel = InventoriesViewModel(repository)

        viewModel.loadIfNeeded()
        viewModel.loadIfNeeded()

        assertEquals(2, repository.getInventoriesCallCount)
    }

    @Test
    fun `onErrorShown を呼ぶと同じエラーを二度通知しない`() = runTest {
        val viewModel = InventoriesViewModel(
            FakeInventoryRepository(failure = ApiException("圏外です。"))
        )

        viewModel.load()
        viewModel.onErrorShown()

        assertEquals(InventoriesUiState.Failed(null), viewModel.uiState.value)
    }

    /** 呼ばれるたびに gate を積み、応答が返る順序をテストから操作できるようにする。 */
    private class SlowRepository : InventoryRepository {
        val gates = mutableListOf<CompletableDeferred<List<Inventory>>>()

        override suspend fun getInventories(): List<Inventory> {
            val gate = CompletableDeferred<List<Inventory>>()
            gates += gate
            return gate.await()
        }

        override suspend fun getInventory(inventoryId: Int): Inventory = error("未使用")
        override suspend fun createInventory(title: String) = error("未使用")
    }
}
