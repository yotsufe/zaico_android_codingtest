package jp.co.zaico.codingtest

import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の InventoryRepository。
 *
 * 呼ばれた内容を記録し、[failure] が指定されていればそれを投げる。
 * [gate] を渡すと complete されるまで作成処理が中断するので、処理中の状態を検証できる。
 */
class FakeInventoryRepository(
    private val inventories: List<Inventory> = emptyList(),
    private val inventory: Inventory = Inventory(id = 0, title = "", quantity = ""),
    private val failure: Throwable? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : InventoryRepository {

    val createdTitles = mutableListOf<String>()
    val requestedInventoryIds = mutableListOf<Int>()
    var getInventoriesCallCount = 0
        private set

    override suspend fun getInventories(): List<Inventory> {
        getInventoriesCallCount++
        gate?.await()
        failure?.let { throw it }
        return inventories
    }

    override suspend fun getInventory(inventoryId: Int): Inventory {
        requestedInventoryIds += inventoryId
        gate?.await()
        failure?.let { throw it }
        return inventory
    }

    override suspend fun createInventory(title: String) {
        createdTitles += title
        gate?.await()
        failure?.let { throw it }
    }
}
