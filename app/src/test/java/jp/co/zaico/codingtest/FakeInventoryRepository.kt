package jp.co.zaico.codingtest

import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の InventoryRepository。
 *
 * 渡されたタイトルを記録し、[failure] が指定されていればそれを投げる。
 * [gate] を渡すと complete されるまで作成処理が中断するので、処理中の状態を検証できる。
 *
 * 差し替えたいのはメソッド 1 つの interface だけなので、モックライブラリを入れずに手書きしている。
 */
class FakeInventoryRepository(
    private val failure: Throwable? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : InventoryRepository {

    val createdTitles = mutableListOf<String>()

    override suspend fun createInventory(title: String) {
        createdTitles += title
        gate?.await()
        failure?.let { throw it }
    }
}
