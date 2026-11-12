package app.thumbcast.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.thumbcast.ui.guide.parseInlineCode

/** Maksymalna szerokość treści na tabletach i w poziomie (treść jest wyśrodkowana). */
internal val ContentMaxWidth = 720.dp

/**
 * Otwieranie i udostępnianie linków z ekranów :control-ui. Brak aplikacji (przeglądarki, share sheet) nie jest
 * błędem: adres trafia do schowka, a ekran pokazuje komunikat (wynik `false`).
 */
internal object LinkActions {
    private const val TAG = "ThumbcastUi"

    /** Strona w przeglądarce (ACTION_VIEW). */
    fun open(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, url.toUri()), url)

    /** Systemowy share sheet z jedną linią podpowiedzi i adresem (np. żeby wysłać link na PC). */
    fun share(context: Context, chooserTitle: String, subject: String, text: String, url: String): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, chooserTitle), url)
    }

    private fun start(context: Context, intent: Intent, url: String): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $url", e)
            copy(context, url)
            false
        }

    private fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    }
}

/**
 * Tekst z zasobów z fragmentami kodu w znakach `` ` `` (adresy, nazwy plików): kod jest pisany czcionką o stałej
 * szerokości na tle powierzchni podniesionej. Rozmiar czcionki idzie za ustawieniem systemu (sp ze stylu).
 */
@Composable
internal fun InlineCodeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated: AnnotatedString = remember(text, codeBackground) {
        buildAnnotatedString {
            for (segment in parseInlineCode(text)) {
                if (segment.isCode) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        // Spacje nierozdzielające po bokach dają tłu kodu oddech, a adres się nie łamie na spacji.
                        append(' ')
                        append(segment.text)
                        append(' ')
                    }
                } else {
                    append(segment.text)
                }
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

/**
 * Krok z ikoną: ikona w kółku 40 dp z małym numerem w rogu i tekst z kodem. Bez ikony (lista ikon krótsza niż
 * `string-array`, np. po dodaniu kroku tylko w tekstach) zostaje sam numer. Czytnik ekranu czyta całość jako jedną
 * pozycję; ikona jest dekoracyjna.
 */
@Composable
internal fun IconStep(number: Int, icon: ImageVector?, text: String, modifier: Modifier = Modifier) {
    if (icon == null) {
        NumberedStep(number, text, modifier)
        return
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        InlineCodeText(text = text, modifier = Modifier.weight(1f).padding(top = 8.dp))
    }
}

/** Kroki z `string-array` z ikonami w tej samej kolejności (ikony: [IconStep]). */
@Composable
internal fun IconSteps(texts: Array<String>, icons: List<ImageVector>) {
    Column(verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium)) {
        texts.forEachIndexed { i, text -> IconStep(number = i + 1, icon = icons.getOrNull(i), text = text) }
    }
}

/** Uwaga pod krokami: mała ikona i tekst drugorzędny (np. płatne zamienniki, podpowiedź). */
@Composable
internal fun NoteRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        InlineCodeText(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Numerowany krok: numer w kółku i tekst z kodem. Czytnik ekranu czyta całość jako jedną pozycję. */
@Composable
internal fun NumberedStep(number: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceSmall + ThumbcastDimens.SpaceXSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .sizeIn(minWidth = 26.dp, minHeight = 26.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        InlineCodeText(text = text, modifier = Modifier.weight(1f))
    }
}
