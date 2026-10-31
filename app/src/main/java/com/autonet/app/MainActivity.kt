package com.autonet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var speechEngine: HybridSpeechEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechEngine = HybridSpeechEngine(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E5FF))) {
                AutoNetScreen(speechEngine)
            }
        }
    }

    override fun onDestroy() {
        speechEngine?.release()
        super.onDestroy()
    }
}

@Composable
fun AutoNetScreen(speechEngine: HybridSpeechEngine?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var textCommand by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf("System Ready. Engines Loading...") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var engineReady by remember { mutableStateOf(false) }

    val permissions = arrayOf(
        Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        permissionsGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (permissionsGranted) {
            outputLog = "Loading Models..."
            CoroutineScope(Dispatchers.IO).launch {
                speechEngine?.initEngines()
                engineReady = true
                outputLog = "Engines Ready. Speak or Type."
            }
        } else {
            outputLog = "Permissions Denied."
        }
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
        } else {
            permissionsGranted = true
            outputLog = "Loading Models..."
            CoroutineScope(Dispatchers.IO).launch {
                speechEngine?.initEngines()
                engineReady = true
                outputLog = "Engines Ready. Speak or Type."
            }
        }
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        val response = CommandRouter.execute(context, command)
        outputLog = response
        speechEngine?.speak(response)
    }

    val neonGradient = Brush.linearGradient(colors = listOf(Color(0xFF000000), Color(0xFF003B46), Color(0xFF001F3F)))

    Column(
        modifier = Modifier.fillMaxSize().background(neonGradient).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AutoNet Ultimate", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
        Text("Offline Super Automator", fontSize = 16.sp, color = Color(0xFF64FFDA), modifier = Modifier.padding(bottom = 24.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xCC001A20)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { 
                        if (!engineReady) { Toast.makeText(context, "Engines loading...", Toast.LENGTH_SHORT).show(); return@Button }
                        if (!permissionsGranted) { Toast.makeText(context, "Grant permissions first", Toast.LENGTH_SHORT).show(); return@Button }
                        isListening = true
                        speechEngine?.startListening { result ->
                            textCommand = result
                            executeCommand(result)
                            isListening = false
                        }
                    },
                    modifier = Modifier.size(100.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isListening) Color(0xFFD50000) else Color(0xFF00E5FF))
                ) { Icon(Icons.Default.Mic, contentDescription = "Mic", modifier = Modifier.size(48.dp), tint = Color.Black) }
                Spacer(modifier = Modifier.height(16.dp))
                Text(if (isListening) "Listening (Offline)..." else "Tap to Speak", color = Color.White, fontSize = 18.sp)
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
                        placeholder = { Text("e.g., turn on bluetooth", color = Color.Gray) },
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
