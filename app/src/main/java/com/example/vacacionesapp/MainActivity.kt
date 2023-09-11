package com.example.vacacionesapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


// Enumeración que representa las pantallas disponibles en la aplicación
enum class Pantalla {
    CapturaFoto,
    MiniaturaFoto,
    Ubicacion
}

// ViewModel para la pantalla principal de la aplicación
class AppVM : ViewModel() {
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    var permisoUbicacionOk: () -> Unit = {}
}

// ViewModel para la funcionalidad de la cámara
class AppCameraVM : ViewModel() {
    val pantallaPrincipal = mutableStateOf(Pantalla.CapturaFoto)
    val ubicacionFoto = mutableStateOf<Location?>(null)
    var onPermisoCamara: () -> Unit = {}
    var onPermisoUbicacion: () -> Unit = {}
    val mostrarMiniatura = mutableStateOf(false)
}

// ViewModel para la gestión del formulario y las fotos
class FormularioVM : ViewModel() {
    val nombre = mutableStateOf("")
    val fotos = mutableStateListOf<Uri>() // Cambio a lista de Uri
}

// Función para generar un nombre de archivo único basado en la fecha y hora actual
fun nombreSegunFecha(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    return now.format(formatter)
}

// Función para crear un archivo en el directorio de imágenes externas con un nombre único
fun archivoPrivado(contexto: Context): File {
    val timeStamp = nombreSegunFecha()
    val directorioFotos = contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File(directorioFotos, "$timeStamp.jpg")
}

// Función para convertir una Uri en un objeto Bitmap
fun imagenUriToBitmap(uri: Uri, contexto: Context): Bitmap =
    BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri))

// Actividad principal de la aplicación
class MainActivity : ComponentActivity() {
    val camaraVm: AppCameraVM by viewModels()
    val formularioVM: FormularioVM by viewModels()

    lateinit var cameraController: LifecycleCameraController

    // Inicialización de ActivityResultLauncher para permisos de cámara
    private val lanzarPermisosCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            camaraVm.onPermisoCamara()
        }
    }

    // Inicialización de ActivityResultLauncher para permisos de ubicación
    private val lanzarPermisosUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            camaraVm.onPermisoUbicacion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización del controlador de la cámara
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // Configuración de la interfaz de usuario
        setContent {
            AppCamera(lanzarPermisosCamara, lanzarPermisosUbicacion, cameraController)
        }
    }
}

// Composable que representa la interfaz de usuario de la cámara
@Composable
fun AppCamera(
    lanzarPermisosCamara: ActivityResultLauncher<String>,
    lanzarPermisosUbicacion: ActivityResultLauncher<String>,
    cameraController: LifecycleCameraController
) {
    val appCameraVM: AppCameraVM = viewModel()
    when (appCameraVM.pantallaPrincipal.value) {
        Pantalla.CapturaFoto -> pantallaCapturaFoto(lanzarPermisosCamara, cameraController, appCameraVM)
        Pantalla.MiniaturaFoto -> pantallaMiniaturaFoto()
        Pantalla.Ubicacion -> pantallaUbicacion()
    }
}

// Composable para la pantalla de captura de fotos
@Composable
fun pantallaCapturaFoto(
    lanzarPermisosCamara: ActivityResultLauncher<String>,
    cameraController: LifecycleCameraController,
    appCameraVM: AppCameraVM
) {
    val contexto = LocalContext.current
    val formularioVM: FormularioVM = viewModel()

    // Solicitar permiso de cámara antes de mostrar la vista previa de la cámara
    lanzarPermisosCamara.launch(android.Manifest.permission.CAMERA)

    // Vista previa de la cámara
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Botón para tomar una foto
        Button(
            onClick = {
                tomarFoto(
                    cameraController,
                    archivoPrivado(contexto),
                    contexto
                ) {
                    formularioVM.fotos.add(it) // Agregar la Uri a la lista de fotos
                    appCameraVM.pantallaPrincipal.value = Pantalla.MiniaturaFoto
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Tomar foto")
        }
        // Botón para ver la ubicación
        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.Ubicacion
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Ver Ubicación")
        }
    }
}

// Función para tomar una foto utilizando la cámara
fun tomarFoto(
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onFotoTomada: (uri: Uri) -> Unit
) {
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    onFotoTomada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Error", exception.message.toString())
            }
        }
    )
}


@Composable
fun pantallaMiniaturaFoto() {
    val appCameraVM: AppCameraVM = viewModel()
    val formularioVM: FormularioVM = viewModel()
    val contexto = LocalContext.current
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        formularioVM.fotos.forEach { uri ->
            val imageBitmap = imagenUriToBitmap(uri, contexto)
            val rotatedImageBitmap = rotateBitmap(imageBitmap, uri, contexto)
            val rotatedImage = rotatedImageBitmap.asImageBitmap()
            // Aplicar rotación aquí

            Image(
                bitmap = rotatedImage,
                contentDescription = "Imagen capturada",
                modifier = Modifier
                    .aspectRatio(1f)
                    .border(1.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                    .padding(4.dp)
            )
        }

        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.Ubicacion
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = "Ver Ubicación")
        }

        if (appCameraVM.mostrarMiniatura.value) {
            Button(
                onClick = {
                    appCameraVM.mostrarMiniatura.value = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Volver")
            }
        }
    }
}

fun rotateBitmap(bitmap: Bitmap, uri: Uri, context: Context): Bitmap {
    val rotation = getBitmapRotation(uri, context)
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

fun getBitmapRotation(uri: Uri, context: Context): Int {
    val exifInterface = androidx.exifinterface.media.ExifInterface(uri.path!!)
    return when (exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

@Composable
fun pantallaUbicacion() {
    val appCameraVM: AppCameraVM = viewModel()
    val appVM: AppVM = viewModel()
    val contexto = LocalContext.current
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AndroidView(
            factory = {
                com.google.android.gms.maps.MapView(it).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
                    controller.setZoom(15.0)
                }
            }, update = {
                it.overlays.removeIf { true }
                it.invalidate()

                appCameraVM.ubicacionFoto.value?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    it.controller.animateTo(geoPoint)

                    val marcador = Marker(it)
                    marcador.position = GeoPoint
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    it.overlays.add(marcador)
                }
            }
        )
        Button(
            onClick = {
                appCameraVM.pantallaPrincipal.value = Pantalla.MiniaturaFoto
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(text = "Volver a Foto")
        }
    }
}



