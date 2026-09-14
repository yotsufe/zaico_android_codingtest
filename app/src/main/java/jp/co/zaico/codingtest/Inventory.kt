package jp.co.zaico.codingtest

import kotlinx.serialization.Serializable

@Serializable
data class Inventory(
    val id: Int,
    val title: String,
    val quantity: String,
)

/**
 * 一覧の取得結果。
 *
 * [skipped] は形が想定と違って読み飛ばした件数。
 */
data class Inventories(
    val items: List<Inventory>,
    val skipped: Int,
)
