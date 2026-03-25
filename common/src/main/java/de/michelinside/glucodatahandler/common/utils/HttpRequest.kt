package de.michelinside.glucodatahandler.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.michelinside.glucodatahandler.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpRequest {

    private var lastResponse: String? = null
    private var lastError: String? = null
    private var lastMessage: String? = null
    private var lastCode: Int = -1
    private var lastHeaderFields: Map<String, List<String>>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val LOG_ID = "GDH.Utils.HttpRequest"
        fun isConnected(context: Context): Boolean {
            try {
                val connectivityManager = context.getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as ConnectivityManager?
                    ?: return false

                val activeNetwork = connectivityManager.activeNetwork ?: return false
                val capabilities =
                    connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

                // If we check only for "NET_CAPABILITY_INTERNET", we get "true" if we are connected to a wifi
                // which has no access to the internet. "NET_CAPABILITY_VALIDATED" also verifies that we
                // are online
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            } catch (exc: Exception) {
                Log.e(LOG_ID, "isConnected exception: " + exc.message.toString())
            }
            return false
        }

        fun isLocalHost(url: String): Boolean {
            return url.contains("127.0.0.1") || url.lowercase().contains("localhost")
        }
    }

    fun stop() {
        reset()
    }

    val connected: Boolean get() = false // Deprecated with local connection
    val responseError: String? get() = lastError
    val responseMessage: String? get() = lastMessage
    val response: String? get() = lastResponse
    val code: Int get() = lastCode

    fun getHeaderField(name: String): String? {
        return lastHeaderFields?.get(name)?.firstOrNull()
    }

    private fun reset() {
        lastResponse = null
        lastError= null
        lastMessage = null
        lastCode = -1
        lastHeaderFields = null
    }

    fun get(url: String, header: MutableMap<String, String>? = null, trustAllCertificates: Boolean = false): Int {
        return request(url, header, null, trustAllCertificates, false)
    }

    fun post(url: String, postData: String?, header: MutableMap<String, String>? = null, trustAllCertificates: Boolean = false): Int {
        return request(url, header, postData, trustAllCertificates, true)
    }

    private fun request(url: String, header: MutableMap<String, String>?, postData: String?, trustAllCertificates: Boolean, postRequest: Boolean): Int = runBlocking {
       scope.async {
            reset()
            var conn: HttpURLConnection? = null
            try {
                val urlConnection = URL(url).openConnection()
                conn = urlConnection as HttpURLConnection

                // 1. SET SSL/Trust BEFORE anything else
                if (trustAllCertificates && conn is HttpsURLConnection) {
                    trustAllCertificates(conn)
                }

                // 2. SET METHOD
                conn.requestMethod = if (postRequest) "POST" else "GET"

                // 3. CONFIGURE TIMEOUTS & FLAGS
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.doInput = true

                header?.forEach { (key, value) ->
                    if (BuildConfig.DEBUG)
                        Log.v(LOG_ID, "Add to header: $key = $value")
                    conn.setRequestProperty(key, value)
                }



                if (postRequest && postData != null) {
                    conn.doOutput = true
                    conn.outputStream.use { os ->
                        val bytes: ByteArray = postData.toByteArray()
                        if (BuildConfig.DEBUG)
                            Log.v(LOG_ID, "Send data: $postData with size ${bytes.size}")
                        os.write(bytes, 0, bytes.size)
                        os.flush()
                    }
                } else {
                    conn.doOutput = false
                }

                Log.i(LOG_ID, "${conn.requestMethod} - request to $url")
                handleResponse(conn)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "request exception: " + exc.toString())
                lastError = exc.toString()
                lastMessage = exc.message
            } finally {
                conn?.disconnect()
            }
            lastCode
        }.await()
    }

    private fun handleResponse(conn: HttpURLConnection) {
        lastCode = conn.responseCode
        lastMessage = conn.responseMessage
        lastHeaderFields = conn.headerFields
        Log.i(LOG_ID, "Code $lastCode received with message $lastMessage")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            if (conn.errorStream != null ) {
                lastError = conn.errorStream.bufferedReader().use { it.readText() }
                Log.e(LOG_ID, "Error received: $lastError")
            }
        } else {
            lastResponse = conn.inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun trustAllCertificates(httpURLConnection: HttpsURLConnection) {
        // trust all certificates (see https://www.baeldung.com/okhttp-client-trust-all-certificates)
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        httpURLConnection.sslSocketFactory = sslContext.socketFactory
        httpURLConnection.setHostnameVerifier { _: String?, _: SSLSession? -> true }
    }
}
