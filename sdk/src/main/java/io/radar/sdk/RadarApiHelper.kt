package io.radar.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Executors

internal open class RadarApiHelper(
    private var logger: RadarLogger? = null
) {
  
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    interface RadarApiCallback {
        fun onComplete(status: Radar.RadarStatus, res: JSONObject? = null)
    }

    internal open fun request(context: Context,
                         method: String,
                         url: URL,
                         headers: Map<String, String>?,
                         params: JSONObject?,
                         sleep: Boolean,
                         callback: RadarApiCallback? = null) {
        logger?.d("📍 Radar API request | method = ${method}; url = ${url}; headers = ${headers}; params = $params")
        
        executor.execute {
            try {
                val urlConnection = url.openConnection() as HttpURLConnection
                if (headers != null) {
                    for ((key, value) in headers) {
                        try {
                            urlConnection.setRequestProperty(key, value)
                        } catch (e: Exception) {

                        }
                    }
                }
                urlConnection.requestMethod = method
                urlConnection.connectTimeout = 10000
                urlConnection.readTimeout = 10000

                if (params != null) {
                    urlConnection.doOutput = true

                    val outputStreamWriter = OutputStreamWriter(urlConnection.outputStream)
                    outputStreamWriter.write(params.toString())
                    outputStreamWriter.close()
                }

                if (urlConnection.responseCode in 200 until 400) {
                    val body = urlConnection.inputStream.readAll()
                    if (body == null) {
                        handler.post {
                            callback?.onComplete(Radar.RadarStatus.ERROR_SERVER)
                        }

                        return@execute
                    }

                    val res = JSONObject(body)

                    logger?.d("📍 Radar API response | method = ${method}; url = ${url}; responseCode = ${urlConnection.responseCode}; res = $res")
                    
                    handler.post {
                        callback?.onComplete(Radar.RadarStatus.SUCCESS, res)
                    }
                } else {
                    val status = when (urlConnection.responseCode) {
                        400 -> Radar.RadarStatus.ERROR_BAD_REQUEST
                        401 -> Radar.RadarStatus.ERROR_UNAUTHORIZED
                        402 -> Radar.RadarStatus.ERROR_PAYMENT_REQUIRED
                        403 -> Radar.RadarStatus.ERROR_FORBIDDEN
                        404 -> Radar.RadarStatus.ERROR_NOT_FOUND
                        429 -> Radar.RadarStatus.ERROR_RATE_LIMIT
                        in (500 until 600) -> Radar.RadarStatus.ERROR_SERVER
                        else -> Radar.RadarStatus.ERROR_UNKNOWN
                    }

                    val body = urlConnection.errorStream.readAll()
                    if (body == null) {
                        callback?.onComplete(Radar.RadarStatus.ERROR_SERVER)

                        return@execute
                    }

                    val res = JSONObject(body)

                    logger?.d("📍 Radar API response | responseCode = ${urlConnection.responseCode}; res = $res")
                    
                    handler.post {
                        callback?.onComplete(status)
                    }
                }

                urlConnection.disconnect()
            } catch (e: IOException) {
                handler.post {
                    callback?.onComplete(Radar.RadarStatus.ERROR_NETWORK)
                }
            } catch (e: JSONException) {
                handler.post {
                    callback?.onComplete(Radar.RadarStatus.ERROR_SERVER)
                }
            } catch (e: Exception) {
                handler.post {
                    callback?.onComplete(Radar.RadarStatus.ERROR_UNKNOWN)
                }
            }

            if (sleep) {
                Thread.sleep(1000)
            }
        }
    }

    private fun InputStream.readAll(): String? {
        val scanner = Scanner(this, "UTF-8").useDelimiter("\\A")
        val body = if (scanner.hasNext()) {
            scanner.next()
        } else {
            null
        }
        close()

        return body
    }

}