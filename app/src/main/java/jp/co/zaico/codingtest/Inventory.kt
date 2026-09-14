package jp.co.zaico.codingtest

import kotlinx.serialization.Serializable

@Serializable
data class Inventory(
    val id: Int,
    val title: String,
    val quantity: String,
)
