package es.ipardelo.chamaxa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ═════════════════════════════════════════════════════════════════════════════
//  AXUSTES XERAIS (os contactos xa NON se configuran aquí, senón desde o móbil)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Tamaño MÁXIMO do nome nos botóns. Se o nome non cabe (p. ex. en 4 × 4),
 * redúcese automaticamente ata TAMANO_TEXTO_MIN sen partir as palabras.
 */
private val TAMANO_TEXTO = 32.sp
private val TAMANO_TEXTO_MIN = 14.sp

/** Bloqueo tras unha pulsación, para evitar chamadas duplicadas. */
private const val BLOQUEO_CHAMADA_MS = 3_000L

/** Grella: entre 1 e 4 columnas e entre 1 e 4 filas (máximo 4 × 4 = 16 contactos). */
private const val MAX_COLUMNAS = 4
private const val MAX_FILAS = 4
private const val NUM_SLOTS = MAX_COLUMNAS * MAX_FILAS
private val OPCIONS_GRELLA = 1..4
private const val COLUMNAS_POR_DEFECTO = 4
private const val FILAS_POR_DEFECTO = 4
private const val MAX_NOME = 20
private const val LADO_FOTO_PX = 512

/** Cores de alto contraste dispoñibles no selector. */
val PALETA = listOf(
    Color(0xFFC62828), // vermello
    Color(0xFF1565C0), // azul
    Color(0xFF2E7D32), // verde
    Color(0xFF6A1B9A), // morado
    Color(0xFFFFD600), // amarelo
    Color(0xFFE65100), // laranxa
    Color(0xFFAD1457), // rosa
    Color(0xFF00838F), // turquesa
)

// ═════════════════════════════════════════════════════════════════════════════
//  MODELO E PERSISTENCIA (SharedPreferences + JSON de Android, sen dependencias)
// ═════════════════════════════════════════════════════════════════════════════

data class Contacto(
    val nome: String = "",
    val telefono: String = "",
    val cor: Int = PALETA[0].toArgb(),
    /** Ruta absoluta dunha copia da foto en filesDir/fotos (non un URI temporal). */
    val foto: String? = null,
    val activo: Boolean = true,
) {
    /** Só se pode chamar se está activo e ten un número. */
    val chamable: Boolean get() = activo && telefono.any { it.isDigit() }
    val baleiro: Boolean get() = nome.isBlank() && telefono.isBlank() && foto == null
}

/** Tamaño da grella da pantalla principal. */
data class Grella(val columnas: Int, val filas: Int) {
    val celas: Int get() = columnas * filas
}

fun contactoBaleiro(indice: Int) = Contacto(cor = PALETA[indice % PALETA.size].toArgb())

object Almacen {
    private const val PREFS = "configuracion_launcher"
    private const val CLAVE_SLOTS = "slots_v1"
    private const val CLAVE_LADO_ANTIGO = "lado_grella" // versións anteriores (grella cadrada)
    private const val CLAVE_COLUMNAS = "columnas_grella"
    private const val CLAVE_FILAS = "filas_grella"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cargarGrella(context: Context): Grella {
        val p = prefs(context)
        val antigo = p.getInt(CLAVE_LADO_ANTIGO, -1)
        val columnas = p.getInt(CLAVE_COLUMNAS, if (antigo > 0) antigo else COLUMNAS_POR_DEFECTO)
        val filas = p.getInt(CLAVE_FILAS, if (antigo > 0) antigo else FILAS_POR_DEFECTO)
        return Grella(
            columnas = columnas.takeIf { it in OPCIONS_GRELLA } ?: COLUMNAS_POR_DEFECTO,
            filas = filas.takeIf { it in OPCIONS_GRELLA } ?: FILAS_POR_DEFECTO,
        )
    }

    @SuppressLint("ApplySharedPref")
    fun gardarGrella(context: Context, grella: Grella) {
        prefs(context).edit()
            .putInt(CLAVE_COLUMNAS, grella.columnas)
            .putInt(CLAVE_FILAS, grella.filas)
            .remove(CLAVE_LADO_ANTIGO)
            .commit()
    }

    fun directorioFotos(context: Context): File =
        File(context.filesDir, "fotos").apply { mkdirs() }

    fun cargar(context: Context): List<Contacto> {
        val gardados: List<Contacto> = try {
            val json = prefs(context).getString(CLAVE_SLOTS, null)
            if (json == null) {
                emptyList()
            } else {
                val array = JSONArray(json)
                List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    val foto = if (o.isNull("foto")) null
                    else o.optString("foto").takeIf { it.isNotBlank() && File(it).exists() }
                    Contacto(
                        nome = o.optString("nome"),
                        telefono = o.optString("telefono"),
                        cor = o.optInt("cor", PALETA[i % PALETA.size].toArgb()),
                        foto = foto,
                        activo = o.optBoolean("activo", true),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return List(NUM_SLOTS) { i -> gardados.getOrNull(i) ?: contactoBaleiro(i) }
    }

    // commit() en vez de apply(): queremos que quede escrito xa, por se apagan o móbil.
    @SuppressLint("ApplySharedPref")
    fun gardar(context: Context, slots: List<Contacto>) {
        val array = JSONArray()
        slots.forEach { c ->
            array.put(
                JSONObject()
                    .put("nome", c.nome)
                    .put("telefono", c.telefono)
                    .put("cor", c.cor)
                    .put("foto", c.foto ?: JSONObject.NULL)
                    .put("activo", c.activo)
            )
        }
        prefs(context).edit().putString(CLAVE_SLOTS, array.toString()).commit()
    }

    /** Borra as fotos copiadas que xa non usa ningún botón. */
    fun limparFotosOrfas(context: Context, slots: List<Contacto>) {
        val usadas = slots.mapNotNull { it.foto }.toSet()
        directorioFotos(context).listFiles()?.forEach { ficheiro ->
            if (ficheiro.absolutePath !in usadas) ficheiro.delete()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ═════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    // O resultado dá igual: chamar() comproba o permiso en cada pulsación
    // e, se non está concedido, abre o marcador (ACTION_DIAL).
    private val solicitudePermisoChamada =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocultarBarrasSistema()

        if (savedInstanceState == null && !tenPermisoChamada(this)) {
            solicitudePermisoChamada.launch(Manifest.permission.CALL_PHONE)
        }

        Almacen.limparFotosOrfas(this, Almacen.cargar(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ocultarBarrasSistema()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Ao volver dunha chamada ou dun selector, as barras poden reaparecer.
        if (hasFocus) ocultarBarrasSistema()
    }

    private fun ocultarBarrasSistema() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NAVEGACIÓN
// ═════════════════════════════════════════════════════════════════════════════

private sealed interface Pantalla {
    data object Principal : Pantalla
    data object Admin : Pantalla
    data class Editor(val indice: Int) : Pantalla
}

@Composable
fun App() {
    val context = LocalContext.current
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Principal) }
    var slots by remember { mutableStateOf(Almacen.cargar(context)) }
    var grella by remember { mutableStateOf(Almacen.cargarGrella(context)) }

    when (val p = pantalla) {
        Pantalla.Principal -> PantallaPrincipal(
            slots = slots,
            grella = grella,
            onAccesoAdmin = { pantalla = Pantalla.Admin },
        )

        Pantalla.Admin -> PantallaAdmin(
            slots = slots,
            grella = grella,
            onCambiarGrella = { nova ->
                grella = nova
                Almacen.gardarGrella(context, nova)
            },
            onEditar = { indice -> pantalla = Pantalla.Editor(indice) },
            onSair = { pantalla = Pantalla.Principal },
        )

        is Pantalla.Editor -> PantallaEditor(
            indice = p.indice,
            orixinal = slots[p.indice],
            onGardar = { novo ->
                slots = slots.toMutableList().also { it[p.indice] = novo }
                Almacen.gardar(context, slots)
                Almacen.limparFotosOrfas(context, slots)
                pantalla = Pantalla.Admin
            },
            onCancelar = {
                Almacen.limparFotosOrfas(context, slots)
                pantalla = Pantalla.Admin
            },
            onInactividade = {
                Almacen.limparFotosOrfas(context, slots)
                pantalla = Pantalla.Principal
            },
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  PANTALLA PRINCIPAL (a que ve a persoa maior)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PantallaPrincipal(slots: List<Contacto>, grella: Grella, onAccesoAdmin: () -> Unit) {
    // Botón Atrás anulado: non pecha nin minimiza nada.
    BackHandler(enabled = true) { /* Non facer nada */ }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var bloqueado by remember { mutableStateOf(false) }
    var indicePulsado by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(bloqueado) {
        if (bloqueado) {
            delay(BLOQUEO_CHAMADA_MS)
            bloqueado = false
            indicePulsado = null
        }
    }

    // Só contan as celas visibles co tamaño de grella actual.
    val senContactos = slots.take(grella.celas).none { it.chamable }
    val separacion = if (maxOf(grella.columnas, grella.filas) >= 4) 4.dp else 6.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(separacion)
        ) {
            for (fila in 0 until grella.filas) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (columna in 0 until grella.columnas) {
                        val indice = fila * grella.columnas + columna
                        val contacto = slots[indice]
                        CelaPrincipal(
                            contacto = contacto,
                            atenuado = bloqueado && indicePulsado != indice,
                            detectarAcceso = indice == 0,
                            mostrarPista = senContactos && indice == 0,
                            onTap = {
                                if (!bloqueado && contacto.chamable) {
                                    bloqueado = true
                                    indicePulsado = indice
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vibrar(context)
                                    chamar(context, contacto.telefono)
                                }
                            },
                            onAccesoAdmin = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vibrar(context, 500)
                                onAccesoAdmin()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(separacion),
                        )
                    }
                }
            }
        }

        // Aviso de primeira execución (non captura toques: a esquina segue funcionando).
        if (senContactos) {
            Text(
                text = stringResource(R.string.aviso_sen_contactos),
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }
    }
}

/**
 * Unha cela da grella. Detecta o toque manualmente para poder distinguir:
 *  · toque normal (soltar antes de 10 s) → chamada
 *  · manter 10 s seguidos (só na cela 1)  → acceso á configuración, SEN chamar ao soltar
 */
@Composable
fun CelaPrincipal(
    contacto: Contacto,
    atenuado: Boolean,
    detectarAcceso: Boolean,
    mostrarPista: Boolean,
    onTap: () -> Unit,
    onAccesoAdmin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapActual by rememberUpdatedState(onTap)
    val accesoActual by rememberUpdatedState(onAccesoAdmin)
    var premido by remember { mutableStateOf(false) }
    val descricionChamar = stringResource(R.string.cd_chamar, contacto.nome)

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                if (contacto.chamable) contentDescription = descricionChamar
                onClick { tapActual(); true }
            }
            .pointerInput(detectarAcceso) {
                awaitEachGesture {
                    awaitFirstDown()
                    premido = true
                    try {
                        if (detectarAcceso) {
                            var expirou = true
                            val soltar = withTimeoutOrNull(TEMPO_ACCESO_ADMIN_MS) {
                                waitForUpOrCancellation().also { expirou = false }
                            }
                            when {
                                expirou -> accesoActual()
                                soltar != null -> {
                                    soltar.consume()
                                    tapActual()
                                }
                            }
                        } else {
                            val soltar = waitForUpOrCancellation()
                            if (soltar != null) {
                                soltar.consume()
                                tapActual()
                            }
                        }
                    } finally {
                        premido = false
                    }
                }
            }
    ) {
        when {
            contacto.chamable -> AparenciaBoton(
                contacto = contacto,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val escala = if (premido) 0.95f else 1f
                        scaleX = escala
                        scaleY = escala
                        alpha = if (atenuado) 0.35f else 1f
                    },
            )

            mostrarPista -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(2.dp, Color(0xFF555555), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.pista_acceso),
                    color = Color(0xFF9E9E9E),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            else -> Unit // Cela baleira ou desactivada: non se ve nada.
        }
    }
}

/** Aspecto visual dun botón de contacto (reutilizado na grella, na configuración e na vista previa). */
@Composable
fun AparenciaBoton(
    contacto: Contacto,
    modifier: Modifier = Modifier,
    tamanoTexto: TextUnit = TAMANO_TEXTO,
) {
    val fondo = Color(contacto.cor)
    val texto = corTexto(fondo)
    val foto = rememberFoto(contacto.foto)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = fondo,
        contentColor = texto,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (foto != null) {
                Image(
                    bitmap = foto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(3.dp, texto, CircleShape),
                )
                Spacer(Modifier.height(6.dp))
            }
            TextoAxustado(
                texto = contacto.nome,
                tamanoMax = tamanoTexto,
            )
        }
    }
}

/**
 * Texto en negriña que reduce o seu tamaño ata caber no espazo dispoñible
 * (máximo 2 liñas e sen partir ningunha palabra pola metade).
 */
@Composable
fun TextoAxustado(
    texto: String,
    tamanoMax: TextUnit,
    tamanoMin: TextUnit = TAMANO_TEXTO_MIN,
) {
    val palabras = remember(texto) { texto.trim().split(Regex("\\s+")).size }
    var tamano by remember(texto, tamanoMax) { mutableStateOf(tamanoMax) }
    var listo by remember(texto, tamanoMax) { mutableStateOf(false) }

    Text(
        text = texto,
        fontSize = tamano,
        lineHeight = tamano * 1.05f,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Clip,
        // Non se debuxa ata atopar o tamaño correcto, para evitar parpadeos.
        modifier = Modifier.drawWithContent { if (listo) drawContent() },
        onTextLayout = { resultado ->
            val palabraPartida = resultado.lineCount > palabras
            if ((resultado.hasVisualOverflow || palabraPartida) && tamano > tamanoMin) {
                tamano = (tamano.value * 0.9f).coerceAtLeast(tamanoMin.value).sp
            } else {
                listo = true
            }
        },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  UTILIDADES DE UI
// ═════════════════════════════════════════════════════════════════════════════

/** Texto negro sobre fondos claros (p. ex. amarelo) e branco sobre escuros. */
private fun corTexto(fondo: Color): Color =
    if (fondo.luminance() > 0.5f) Color.Black else Color.White

// ═════════════════════════════════════════════════════════════════════════════
//  SISTEMA: chamadas, vibración, axenda, fotos, launcher
// ═════════════════════════════════════════════════════════════════════════════

private fun tenPermisoChamada(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Chama directamente con ACTION_CALL. Se non hai permiso ou algo falla,
 * abre o marcador co número xa escrito (ACTION_DIAL). Nunca lanza excepcións.
 */
private fun chamar(context: Context, telefono: String) {
    val numero = telefono.filter { it.isDigit() || it == '+' }
    val uri = Uri.fromParts("tel", numero, null)

    if (tenPermisoChamada(context)) {
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // SecurityException, ActivityNotFoundException… → probamos o marcador.
        }
    }
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // Sen app de teléfono: non facemos nada, pero a app non se pecha.
    }
}

/** Vibración física adicional, máis perceptible que o feedback háptico estándar. */
private fun vibrar(context: Context, duracionMs: Long = 200) {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duracionMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Android 5.0 – 7.1
            @Suppress("DEPRECATION")
            vibrator.vibrate(duracionMs)
        }
    } catch (_: Exception) {
    }
}
