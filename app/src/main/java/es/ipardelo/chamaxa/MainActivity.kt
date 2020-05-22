package es.ipardelo.chamaxa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ═════════════════════════════════════════════════════════════════════════════
//  AXUSTES XERAIS (os contactos xa NON se configuran aquí, senón desde o móbil)
// ═════════════════════════════════════════════════════════════════════════════

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
