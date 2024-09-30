package com.example.lab08

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import com.example.lab08.ui.theme.Lab08Theme
import androidx.compose.material3.Card
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())

    // Runnable para enviar notificaciones
    private val notificationRunnable = object : Runnable {
        override fun run() {
            sendNotification()
            handler.postDelayed(this, 5 * 60 * 1000) // Repetir cada 5 minutos
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // El permiso fue concedido, puedes iniciar las notificaciones
                startSendingNotifications()
            } else {
                // El permiso fue denegado, puedes mostrar un mensaje al usuario
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solicitar el permiso de notificación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // El permiso ya está concedido, iniciar las notificaciones
            startSendingNotifications()
        }

        setContent {
            Lab08Theme {
                val db = Room.databaseBuilder(
                    applicationContext,
                    TaskDatabase::class.java,
                    "task_db"
                ).build()

                val taskDao = db.taskDao()
                val viewModel = TaskViewModel(taskDao)

                TaskScreen(viewModel)
            }
        }
    }

    private fun startSendingNotifications() {
        handler.post(notificationRunnable) // Inicia el envío de notificaciones
    }

    private fun sendNotification() {
        val context = this // O usa LocalContext.current si estás en un Composable
        val channelId = "task_reminder_channel"

        // Crear el canal de notificación solo en Android O y versiones superiores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorio de Tareas"
            val descriptionText = "Canal para recordatorios de tareas"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Crear la notificación
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Recordatorio de Tareas")
            .setContentText("Recuerda completar tus tareas pendientes.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Verificar permisos antes de mostrar la notificación
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(context)) {
                notify(1, builder.build()) // Mostrar la notificación
            }
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var newTaskDescription by remember { mutableStateOf("") }
    var showCompleted by remember { mutableStateOf(false) } // Estado para controlar el filtro

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (newTaskDescription.isNotEmpty()) {
                        coroutineScope.launch {
                            viewModel.addTask(newTaskDescription)
                            newTaskDescription = ""
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar tarea")
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Lista de Tareas",
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.deleteAllTasks() // Eliminar todas las tareas
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar todas las tareas")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            TextField(
                value = newTaskDescription,
                onValueChange = { newTaskDescription = it },
                label = { Text("Nueva tarea") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Agregar el componente de filtro
            TaskFilter(
                onShowCompleted = { showCompleted = true },
                onShowPending = { showCompleted = false }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Filtrar las tareas según el estado
                val filteredTasks = if (showCompleted) {
                    tasks.filter { it.isCompleted }
                } else {
                    tasks.filter { !it.isCompleted }
                }

                items(filteredTasks) { task ->
                    TaskItem(
                        task = task,
                        onToggleTaskCompletion = {
                            viewModel.toggleTaskCompletion(task)
                        },
                        onDeleteTask = {
                            coroutineScope.launch {
                                viewModel.deleteTask(task)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    onToggleTaskCompletion: () -> Unit,
    onDeleteTask: () -> Unit // Agregamos un nuevo parámetro para la eliminación
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
            Row {
                IconButton(onClick = onToggleTaskCompletion) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (task.isCompleted) "Tarea completada" else "Tarea pendiente"
                    )
                }
                IconButton(onClick = onDeleteTask) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar tarea") // Icono para eliminar
                }
            }
        }
    }
}

@Composable
fun TaskFilter(
    onShowCompleted: () -> Unit,
    onShowPending: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = onShowPending) {
            Text("Pendientes")
        }
        Button(onClick = onShowCompleted) {
            Text("Completadas")
        }
    }
}
