package jp.co.zaico.codingtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZaicoApiEndpointTest {

    @Test
    fun `urlOf は baseUrl 末尾と path 先頭のスラッシュを重複させない`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "t")

        assertEquals(
            "https://example.test/api/v2/orgs/companies.json",
            endpoint.urlOf("/api/v2/orgs/companies.json"),
        )
    }

    @Test
    fun `urlOf は baseUrl に末尾スラッシュが無くても連結できる`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test", token = "t")

        assertEquals("https://example.test/api/v2/x.json", endpoint.urlOf("/api/v2/x.json"))
    }

    @Test
    fun `urlOf は path に先頭スラッシュが無くても連結できる`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "t")

        assertEquals("https://example.test/api/v2/x.json", endpoint.urlOf("api/v2/x.json"))
    }

    @Test
    fun `authorizationHeader は Bearer 形式のトークンを返す`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "test-token")

        assertEquals("Bearer test-token", endpoint.authorizationHeader)
    }

    @Test
    fun `hasToken はトークンが設定されていれば true を返す`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "test-token")

        assertTrue(endpoint.hasToken)
    }

    @Test
    fun `hasToken はトークンが空なら false を返す`() {
        val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "")

        assertFalse(endpoint.hasToken)
    }
}
