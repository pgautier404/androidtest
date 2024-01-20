package com.example.httptesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import javax.net.ssl.HttpsURLConnection

const val baseApiUrl = "https://57zovcekn0.execute-api.us-west-2.amazonaws.com/prod"
val supportedLanguages = mutableMapOf<String, String>()
var messages = mutableStateOf("Loading messages...")
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
                    Greeting("pete")
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
                launch { getApiToken(name) }
                launch { getSupportedLanguages() }
            }
            println("Supported languages are ${supportedLanguages.toString()}")
            val credentialProvider = CredentialProvider.fromString(momentoApiToken)
            val topicClient = TopicClient(
                credentialProvider = credentialProvider,
                configuration = TopicConfigurations.Laptop.latest
            )
            launch { topicSubscribe(topicClient) }
        }
    }
    GreetingLayout(
        modifier = modifier
    )
}

@Composable
fun GreetingLayout(
    modifier: Modifier = Modifier
) {
    var currentLanguage by remember { mutableStateOf("en") }
    Column (
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LanguageDropdown(
            modifier = modifier.fillMaxWidth(),
            language = currentLanguage,
            onLanguageChange = {
                currentLanguage = it
                println("language changed to $currentLanguage")
            }
        )
        MessageList(language = currentLanguage)
    }
}

@Composable
fun MessageList(
    language: String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(language) {
        withContext(Dispatchers.IO) {
            launch { getMessagesForLanguage(language) }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .wrapContentSize(align = Alignment.Center)
            .background(color = Color.Green)
            .padding(4.dp)
    ) {
        Text(
            text = messages.value
        )
    }
}

@Composable
fun LanguageDropdown(
    modifier: Modifier = Modifier,
    language: String,
    onLanguageChange: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.TopStart)
            .padding(8.dp)
    ) {
        println("rendering ddl")
        println("langs: ${supportedLanguages.toString()}")
        Button(onClick = { menuExpanded = !menuExpanded }) {
            Text(text = supportedLanguages[language] ?: "Please Choose")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = {
                menuExpanded = false
                println("menu rollup lang is $language")
            }
        ) {
            for (languageItem in supportedLanguages.entries.iterator()) {
                DropdownMenuItem(
                    text = { Text(languageItem.value) },
                    onClick = {
                        onLanguageChange(languageItem.key)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

suspend fun topicSubscribe(topicClient: TopicClient) {
    when (val response = topicClient.subscribe("moderator", "chat-en")) {
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

private fun getApiToken(username: String) {
    val apiUrl = "$baseApiUrl/v1/translate/token"
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
    val apiUrl = "$baseApiUrl/v1/translate/languages"
    val json = URL(apiUrl).readText()
    val jsonObject = JSONObject(json)
    val languages = jsonObject.getJSONArray("supportedLanguages")
    for (i in 0..<languages.length()) {
        val language = languages.getJSONObject(i)
        val value = language.getString("value")
        val label = language.getString("label")
        supportedLanguages[value] = label
    }
}

private fun getMessagesForLanguage(languageCode: String) {
    val apiUrl = "$baseApiUrl/v1/translate/latestMessages/$languageCode"
    println(apiUrl)
    val json = URL(apiUrl).readText()
    messages.value = json
    println("Got messages: ${messages.value}")
}
