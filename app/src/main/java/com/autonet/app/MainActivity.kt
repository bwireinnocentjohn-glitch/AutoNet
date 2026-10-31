package com.autonet.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E5FF))) {
                AutoNetScreen(tts)
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun AutoNetScreen(tts: TextToSpeech?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showSplash by remember { mutableStateOf(true) }
    var textCommand by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf("System Ready. Say or Type a command.") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    val permissions = arrayOf(
        Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        permissionsGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        outputLog = if (permissionsGranted) "All Systems Online." else "Permissions Denied."
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions)
        } else { permissionsGranted = true }
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        val response = CommandRouter.execute(context, command)
        outputLog = response
        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    DisposableEffect(Unit) {
        val listener = object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; outputLog = "Listening..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false; outputLog = "Voice Error. Please type." }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    textCommand = matches[0]
                    executeCommand(textCommand)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    val neonGradient = Brush.linearGradient(colors = listOf(Color(0xFF000000), Color(0xFF003B46), Color(0xFF001F3F)))

    if (showSplash) {
        Column(
            modifier = Modifier.fillMaxSize().background(neonGradient),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AutoNet", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Engineered by Ajon Inno", fontSize = 18.sp, color = Color(0xFF64FFDA))
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(neonGradient).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AutoNet Lite", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            Text("Fast Voice Automator", fontSize = 16.sp, color = Color(0xFF64FFDA), modifier = Modifier.padding(bottom = 24.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xCC001A20)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { 
                            if (!permissionsGranted) { Toast.makeText(context, "Grant permissions first", Toast.LENGTH_SHORT).show(); return@Button }
                            speechRecognizer.startListening(speechIntent)
                        },
                        modifier = Modifier.size(100.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isListening) Color(0xFFD50000) else Color(0xFF00E5FF))
                    ) { Icon(Icons.Default.Mic, contentDescription = "Mic", modifier = Modifier.size(48.dp), tint = Color.Black) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isListening) "Listening..." else "Tap to Speak", color = Color.White, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xCC001A20)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type Command", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = textCommand, onValueChange = { textCommand = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text("e.g., calculate 5 plus 10", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { executeCommand(textCommand) }) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF00E5FF)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Console", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(outputLog, color = Color(0xFF64FFDA), fontSize = 16.sp)
                }
            }
        }
    }
}
