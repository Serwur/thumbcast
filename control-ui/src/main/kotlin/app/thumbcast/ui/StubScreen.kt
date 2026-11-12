package app.thumbcast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Zaślepka ekranu (P0): tytuł, opcjonalny opis i „Zamknij”. P1 zastępuje ją kreatorem i poradnikiem. */
@Composable
internal fun StubScreen(title: String, detail: String?, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(ThumbcastDimens.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(ThumbcastDimens.SpaceMedium),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.ui_stub_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onClose, modifier = Modifier.heightIn(min = ThumbcastDimens.TouchTarget)) {
                Text(stringResource(R.string.ui_close))
            }
        }
    }
}
