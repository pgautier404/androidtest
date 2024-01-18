package com.example.httptesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.httptesting.ui.theme.HTTPTestingTheme
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import javax.net.ssl.HttpsURLConnection

val baseApiUrl = "https://57zovcekn0.execute-api.us-west-2.amazonaws.com/prod"
val supportedLanguages = mutableMapOf<String, String>()
var momentoApiToken: String = ""
var tokenExpiresAt: Int = 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HTTPTestingTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Dude")
                }
            }
        }
        getSupportedLanguages()
        getApiToken()
        println("token is $momentoApiToken and expires at $tokenExpiresAt")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HTTPTestingTheme {
        Greeting("Android")
    }
}

private fun getApiToken() {
    val apiUrl = "$baseApiUrl/v1/translate/token"
    val queue = LinkedBlockingQueue<String>()

    // These will be inputs
    val username = "peteg"
    val id = UUID.randomUUID()

    Thread {
        var reqParams = URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(username, "UTF-8")
        reqParams += "&" + URLEncoder.encode("id", "UTF-8") + "=" + URLEncoder.encode(id.toString(), "UTF-8")
        val url = URL(apiUrl)
        with (url.openConnection() as HttpsURLConnection) {
            requestMethod = "POST"
            val wr = OutputStreamWriter(outputStream)
            wr.write(reqParams)
            wr.flush()

            println("URL : $url")
            println("Response Code : $responseCode")

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                println("Response : $response")
                queue.add(response.toString())
            }
        }
    }.start()

    val jsonObject = JSONObject(queue.take())
    momentoApiToken = jsonObject.getString("token")
    tokenExpiresAt = jsonObject.getInt("expiresAtEpoch")
}

private fun getSupportedLanguages() {
    val apiURL = "$baseApiUrl/v1/translate/languages"
    val queue = LinkedBlockingQueue<String>()

    Thread {
        val json = getUrl(apiURL)
        queue.add(json)
    }.start()

    val jsonObject = JSONObject(queue.take())
    val languages = jsonObject.getJSONArray("supportedLanguages")
    for (i in 0..<languages.length()) {
        val language = languages.getJSONObject(i)
        val value = language.getString("value")
        val label = language.getString("label")
        supportedLanguages[value] = label
    }
}

private fun getUrl(url: String): String {
    return URL(url).readText()
}
