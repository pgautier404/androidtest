package com.example.httptesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.httptesting.ui.theme.HTTPTestingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import software.momento.kotlin.sdk.TopicClient
import software.momento.kotlin.sdk.auth.CredentialProvider
import software.momento.kotlin.sdk.config.TopicConfigurations
import software.momento.kotlin.sdk.responses.topic.TopicMessage
import software.momento.kotlin.sdk.responses.topic.TopicSubscribeResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import javax.net.ssl.HttpsURLConnection

const val baseApiUrl = "https://57zovcekn0.execute-api.us-west-2.amazonaws.com/prod"
val supportedLanguages = mutableMapOf<String, String>()
var momentoApiToken: String = ""
var tokenExpiresAt: Int = 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Move to suspend fun
        getSupportedLanguages()

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
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    LaunchedEffect(name) {
        withContext(Dispatchers.IO) {
            coroutineScope {
                launch { getApiToken() }
            }
            val credentialProvider = CredentialProvider.fromString(momentoApiToken)
            val topicClient = TopicClient(
                credentialProvider = credentialProvider,
                configuration = TopicConfigurations.Laptop.latest
            )
            launch { topicSubscribe(topicClient) }
        }
    }
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

suspend fun topicSubscribe(topicClient: TopicClient) {
    when (val response = topicClient.subscribe("cache", "chat-en")) {
        is TopicSubscribeResponse.Subscription -> coroutineScope {
            launch {
                withTimeoutOrNull(5_000_000) {
                    response.collect { item ->
                        when (item) {
                            is TopicMessage.Text -> println("Received text message: ${item.value}")
                            is TopicMessage.Binary -> println("Received binary message: ${item.value}")
                            is TopicMessage.Error -> throw RuntimeException(
                                "An error occurred reading messages from topic 'test-topic': ${item.errorCode}", item
                            )
                        }
                    }
                }
            }
        }

        is TopicSubscribeResponse.Error -> throw RuntimeException(
            "An error occurred while attempting to subscribe to topic 'test-topic': ${response.errorCode}", response
        )
    }
}

fun getApiToken() {
    val apiUrl = "$baseApiUrl/v1/translate/token"

    // These will be inputs
    val username = "peteg"
    val id = UUID.randomUUID()

    var reqParams = URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(username, "UTF-8")
    reqParams += "&" + URLEncoder.encode("id", "UTF-8") + "=" + URLEncoder.encode(id.toString(), "UTF-8")
    val url = URL(apiUrl)
    with (url.openConnection() as HttpsURLConnection) {
        requestMethod = "POST"
        val wr = OutputStreamWriter(outputStream)
        wr.write(reqParams)
        wr.flush()

        BufferedReader(InputStreamReader(inputStream)).use {
            val response = StringBuffer()

            var inputLine = it.readLine()
            while (inputLine != null) {
                response.append(inputLine)
                inputLine = it.readLine()
            }
            val jsonObject = JSONObject(response.toString())
            momentoApiToken = jsonObject.getString("token")
            tokenExpiresAt = jsonObject.getInt("expiresAtEpoch")
        }
    }
}

private fun getSupportedLanguages() {
    val apiURL = "$baseApiUrl/v1/translate/languages"
    val queue = LinkedBlockingQueue<String>()

    Thread {
        val json = URL(apiURL).readText()
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
