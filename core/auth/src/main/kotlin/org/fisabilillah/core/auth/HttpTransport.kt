package org.fisabilillah.core.auth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one place this module touches the network.
 *
 * A port rather than a direct call, for two reasons. The obvious one is that the whole
 * gateway becomes testable on a plain JVM with no server anywhere — which is what lets the
 * error mapping, the token refresh window and the account-enumeration defences below be
 * covered by real assertions in CI rather than by hand on a device.
 *
 * The less obvious one is that it keeps a dependency out of the build. An HTTP client
 * library would have to be resolved from a repository the restricted build environment
 * cannot always reach, and `HttpURLConnection` — which every JVM and every Android runtime
 * already has — is entirely adequate for six JSON endpoints.
 */
public interface HttpTransport {
    public suspend fun send(request: HttpRequest): HttpResponse
}

public data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

public data class HttpResponse(
    val status: Int,
    val body: String,
) {
    public val isSuccess: Boolean get() = status in 200..299
}

/** Raised only for a genuine transport failure; an HTTP error status is a normal response. */
public class HttpTransportException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * The default transport.
 *
 * Times out rather than hanging: a sign-in screen that spins forever is indistinguishable
 * from a broken app, and the person holding the phone has no way to tell which it is.
 */
public class JdkHttpTransport(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
) : HttpTransport {

    override suspend fun send(request: HttpRequest): HttpResponse = withContext(dispatcher) {
        val url = URL(request.url)
        require(url.protocol == "https") {
            "Refusing to send an auth request over ${url.protocol}"
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            for ((name, value) in request.headers) setRequestProperty(name, value)
            if (request.body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            request.body?.let { body ->
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            // An error status puts the payload on errorStream, and that payload is where
            // the reason lives, so both are read the same way.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            HttpResponse(status, text)
        } catch (e: IOException) {
            throw HttpTransportException("Could not reach ${url.host}", e)
        } finally {
            connection.disconnect()
        }
    }
}
