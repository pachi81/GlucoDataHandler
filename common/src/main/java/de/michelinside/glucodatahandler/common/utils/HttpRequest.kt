package de.michelinside.glucodatahandler.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.DataOutputStream
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

    private var httpURLConnection: HttpURLConnection? = null
    private var lastResponse: String? = null
    private var lastError: String? = null
    private var lastMessage: String? = null
    private var lastCode: Int = -1
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
    }

    private fun close() {
        if (httpURLConnection != null) {
            Log.v(LOG_ID, "Closing http connection")
            httpURLConnection!!.disconnect()
            httpURLConnection = null
        }
    }

    fun stop() {
        close()
        reset()
    }

    val connected: Boolean get() = httpURLConnection != null
    val responseError: String? get() = lastError
    val responseMessage: String? get() = lastMessage
    val response: String? get() = lastResponse
    val code: Int get() = lastCode

    fun getHeaderField(name: String): String? {
        if (httpURLConnection != null) {
            val header = httpURLConnection!!.getHeaderField(name)
            if (header != null) {
                return header
            }
        }
        return null
    }

    private fun reset() {
        lastResponse = null
        lastError= null
        lastMessage = null
        lastCode = -1
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
            val urlConnection = URL(url).openConnection()
            if (trustAllCertificates && urlConnection is HttpsURLConnection) {
                trustAllCertificates(urlConnection)
            }

            httpURLConnection = urlConnection as HttpURLConnection
            if (!header.isNullOrEmpty()) {
                header.forEach {
                    if (BuildConfig.DEBUG)
                        Log.v(LOG_ID, "Add to header: ${it.key} = ${it.value}")
                    httpURLConnection!!.setRequestProperty(it.key, it.value)
                }
            }
            httpURLConnection!!.doInput = true
            httpURLConnection!!.connectTimeout = 10000
            httpURLConnection!!.readTimeout = 20000
            if (!postRequest) {
                Log.i(LOG_ID, "Send GET request to ${httpURLConnection!!.url}")
                httpURLConnection!!.requestMethod = "GET"
                httpURLConnection!!.doOutput = false
            } else {
                Log.i(LOG_ID, "Send POST request to ${httpURLConnection!!.url}")
                httpURLConnection!!.requestMethod = "POST"
                if (postData != null) {
                    httpURLConnection!!.doOutput = true
                    val dataOutputStream = DataOutputStream(httpURLConnection!!.outputStream)
                    val bytes: ByteArray = postData.toByteArray()
                    Log.d(LOG_ID, "Send data: $postData with size ${bytes.size}")
                    dataOutputStream.write(bytes, 0, bytes.size)
                } else {
                    httpURLConnection!!.doOutput = false
                }
            }
            handleResponse()
            lastCode
        }.await()
    }

    private fun handleResponse() {
        reset()
        if(httpURLConnection != null) {
            lastCode = httpURLConnection!!.responseCode
            lastMessage = httpURLConnection!!.responseMessage
            Log.i(LOG_ID, "Code $lastCode received with message $lastMessage")
            if (httpURLConnection!!.responseCode != HttpURLConnection.HTTP_OK) {
                if (httpURLConnection!!.errorStream != null ) {
                    lastError = httpURLConnection!!.errorStream.bufferedReader().readText()
                    Log.e(LOG_ID, "Error received: $lastError")
                }
            } else {
                lastResponse = httpURLConnection!!.inputStream.bufferedReader().readText()
            }
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