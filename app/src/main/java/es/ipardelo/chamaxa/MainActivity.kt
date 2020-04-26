package es.ipardelo.chamaxa

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ═════════════════════════════════════════════════════════════════════════════
//  AXUSTES XERAIS (os contactos xa NON se configuran aquí, senón desde o móbil)
// ═════════════════════════════════════════════════════════════════════════════

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

fun contactoBaleiro(indice: Int) = Contacto(cor = PALETA[indice % PALETA.size].toArgb())
