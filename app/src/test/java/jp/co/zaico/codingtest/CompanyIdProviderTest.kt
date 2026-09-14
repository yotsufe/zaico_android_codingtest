package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyIdProviderTest {

    private val endpoint = ZaicoApiEndpoint(baseUrl = "https://example.test/", token = "test-token")

    @Test
    fun `先頭の会社の id を返す`() = runTest {
        val engine = MockEngine { respond("""{"data":[{"id":42},{"id":99}]}""") }

        HttpClient(engine).use { client ->
            assertEquals(42, CompanyIdProvider(endpoint).resolve(client))
        }
    }

    @Test
    fun `会社一覧のエンドポイントを叩く`() = runTest {
        val engine = MockEngine { respond("""{"data":[{"id":42}]}""") }

        HttpClient(engine).use { client ->
            CompanyIdProvider(endpoint).resolve(client)
        }

        assertEquals("https://example.test/api/v2/orgs/companies.json", engine.requestHistory.single().url.toString())
    }

    @Test
    fun `2 回目以降は保持した値を返し、API を叩かない`() = runTest {
        val engine = MockEngine { respond("""{"data":[{"id":42}]}""") }
        val provider = CompanyIdProvider(endpoint)

        HttpClient(engine).use { client ->
            assertEquals(42, provider.resolve(client))
            assertEquals(42, provider.resolve(client))
            assertEquals(42, provider.resolve(client))
        }

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `同時に呼ばれても API は 1 回しか叩かない`() = runTest {
        // Mutex を外すとこのテストが落ちる。逐次呼び出しのテストでは検出できない。
        //
        // 2 本目の async を release.complete より前に置くことで順序を決めている。
        // 逆にすると 1 本目が先に完走し、競合が再現しない。
        val arrived = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MockEngine {
            arrived.complete(Unit)
            release.await()
            respond("""{"data":[{"id":42}]}""")
        }
        val provider = CompanyIdProvider(endpoint)

        HttpClient(engine).use { client ->
            val first = async { provider.resolve(client) }
            arrived.await() // 1 本目が通信に入るまで待つ
            val second = async { provider.resolve(client) }
            release.complete(Unit)

            assertEquals(42, first.await())
            assertEquals(42, second.await())
        }

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `取得に失敗しても保持せず、次の呼び出しで取り直す`() = runTest {
        // 失敗をキャッシュすると、一時的な通信エラーから復帰できなくなる。
        val responses = ArrayDeque(listOf("""{"data":[]}""", """{"data":[{"id":42}]}"""))
        val engine = MockEngine { respond(responses.removeFirst()) }
        val provider = CompanyIdProvider(endpoint)

        HttpClient(engine).use { client ->
            assertTrue(errorOf { provider.resolve(client) } is ApiException)

            assertEquals(42, provider.resolve(client))
        }

        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `キャンセルされてもロックを取り残さず、次の呼び出しが取り直せる`() = runTest {
        // ロックを保持したまま通信するため、途中でキャンセルされたときに解放し損ねると
        // 以降 resolve を呼んだ全員が永久に待つ。画面が丸ごと固まる壊れ方になる。
        val arrived = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // requestHistory は完了したリクエストしか記録しないので、キャンセルされた取得は
        // 数に入らない。叩き直したことを見るにはハンドラの呼び出し回数を数える必要がある。
        val attempts = AtomicInteger()
        val engine = MockEngine {
            attempts.incrementAndGet()
            arrived.complete(Unit)
            release.await()
            respond("""{"data":[{"id":42}]}""")
        }
        val provider = CompanyIdProvider(endpoint)

        HttpClient(engine).use { client ->
            val first = async { provider.resolve(client) }
            arrived.await() // ロックを保持し、通信に入った状態にする
            first.cancel()
            release.complete(Unit)

            assertTrue(errorOf { first.await() } is CancellationException)

            // ロックが解放されていなければ、ここで待ち続けてテストがタイムアウトする。
            assertEquals(42, provider.resolve(client))
        }

        // キャンセルされた取得は保持されないので、あらためて叩き直している。
        assertEquals(2, attempts.get())
    }

    @Test
    fun `インスタンスが違えば保持は共有されない`() = runTest {
        val engine = MockEngine { respond("""{"data":[{"id":42}]}""") }

        HttpClient(engine).use { client ->
            CompanyIdProvider(endpoint).resolve(client)
            CompanyIdProvider(endpoint).resolve(client)
        }

        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `会社が 0 件なら ApiException を投げる`() = runTest {
        val engine = MockEngine { respond("""{"data":[]}""") }

        val error = HttpClient(engine).use { client ->
            errorOf { CompanyIdProvider(endpoint).resolve(client) }
        }

        assertTrue(error is ApiException)
        assertEquals("利用可能な会社が見つかりませんでした", error.message)
    }

    @Test
    fun `会社の形が想定と違えば ApiException に変換する`() = runTest {
        // 文言は ZaicoApi 側が持つので、ここでは型だけを固定する。
        // 文字列まで写すと ZaicoApi の文言を変えたときに無関係なテストが落ちる。
        val bodies = listOf(
            """{"data":[1]}""", // 会社がオブジェクトでない
            """{"data":[{"name":"zaico"}]}""", // id が無い
            """{"data":[{"id":"abc"}]}""", // id が整数でない
        )

        for (body in bodies) {
            val engine = MockEngine { respond(body) }

            val error = HttpClient(engine).use { client ->
                errorOf { CompanyIdProvider(endpoint).resolve(client) }
            }

            assertTrue("$body: $error", error is ApiException)
        }
    }

    @Test
    fun `トークンが空ならリクエストを送らずに ApiTokenMissingException を投げる`() = runTest {
        val engine = MockEngine { respond("") }
        val tokenless = endpoint.copy(token = "")

        val error = HttpClient(engine).use { client ->
            errorOf { CompanyIdProvider(tokenless).resolve(client) }
        }

        assertTrue(error is ApiTokenMissingException)
        assertEquals(0, engine.requestHistory.size)
    }

    /**
     * [block] が投げた例外を返す。投げなければテストを失敗させる。
     *
     * `assertThrows` に `runTest` を渡す形にすると、検査対象が `resolve` の例外ではなく
     * `runTest` が後処理を経て再送出した例外になるため、この形にしている。
     */
    private suspend fun errorOf(block: suspend () -> Unit): Throwable {
        var thrown: Throwable? = null
        try {
            block()
        } catch (e: Throwable) {
            thrown = e
        }
        return thrown ?: throw AssertionError("例外が投げられなかった")
    }
}
