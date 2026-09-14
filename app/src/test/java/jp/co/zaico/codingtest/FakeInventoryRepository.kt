package jp.co.zaico.codingtest

import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の InventoryRepository。
 *
 * 呼ばれた内容を記録し、[failure] が指定されていればそれを投げる。
 * [latch] を渡すと complete されるまで全メソッドが中断するので、処理中の状態を検証できる。
 * `CompletableDeferred<Unit>` を latch として使う定番の形で、片側で await し、
 * テスト側が complete して先へ進める。
 */
class FakeInventoryRepository(
    private val inventories: List<Inventory> = emptyList(),
    private val inventory: Inventory = Inventory(id = 0, title = "", quantity = ""),
    private val failure: Throwable? = null,
    private val latch: CompletableDeferred<Unit>? = null,
    private val skipped: Int = 0,
) : InventoryRepository {

    val createdTitles = mutableListOf<String>()
    val requestedInventoryIds = mutableListOf<Int>()
    var getInventoriesCallCount = 0
        private set

    override suspend fun getInventories(): Inventories {
        getInventoriesCallCount++
        latch?.await()
        failure?.let { throw it }
        return Inventories(items = inventories, skipped = skipped)
    }

    override suspend fun getInventory(inventoryId: Int): Inventory {
        requestedInventoryIds += inventoryId
        latch?.await()
        failure?.let { throw it }
        return inventory
    }

    override suspend fun createInventory(title: String) {
        createdTitles += title
        latch?.await()
        failure?.let { throw it }
    }
}
