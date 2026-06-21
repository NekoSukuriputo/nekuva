package org.nekosukuriputo.nekuva.core.os

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/** Doki VoiceInputContract: launch the system speech recognizer, return the top transcription. */
private class VoiceInputContract : ActivityResultContract<String?, String?>() {
    override fun createIntent(context: Context, input: String?): Intent {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration).get(0) ?: Locale.getDefault()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else {
            null
        }
}

@Composable
actual fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!available) return null
    val launcher = rememberLauncherForActivityResult(VoiceInputContract()) { result ->
        if (!result.isNullOrBlank()) onResult(result)
    }
    return remember(launcher) { { launcher.launch(null) } }
}
