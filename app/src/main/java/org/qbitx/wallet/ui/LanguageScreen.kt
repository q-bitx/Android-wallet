package org.qbitx.wallet.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.qbitx.wallet.R
import org.qbitx.wallet.ui.theme.*
import java.util.Locale

object LanguagePreferences {
    val languages=listOf(
        "en" to "English",
        "ru" to "Русский",
        "zh-CN" to "简体中文",
        "de" to "Deutsch",
        "es" to "Español",
        "hi" to "हिन्दी",
        "fr" to "Français",
        "pt" to "Português",
        "ar" to "العربية",
        "tr" to "Türkçe",
        "vi" to "Tiếng Việt",
        "id" to "Bahasa Indonesia",
        "uk" to "Українська",
        "ur" to "اردو",
        "fil" to "Filipino"
    )
    private fun preferences(context: Context)=context.getSharedPreferences("qbx-interface",Context.MODE_PRIVATE)
    fun selected(context: Context): String?=preferences(context).getString("language",null)?.takeIf { tag -> languages.any { it.first==tag } }
    fun select(context: Context,tag: String) {
        require(languages.any { it.first==tag })
        check(preferences(context).edit().putString("language",tag).commit()) { "Language preference could not be saved" }
    }
    fun wrap(context: Context): Context {
        val tag=selected(context) ?: return context
        return localized(context,tag)
    }
    fun localized(context: Context,tag: String): Context {
        require(languages.any { it.first==tag })
        val locale=Locale.forLanguageTag(tag)
        val configuration=Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}

@Composable
fun LanguageScreen(onSelect: (String)->Unit) {
    Surface(Modifier.fillMaxSize(),color=QBXBackground) {
        Column(Modifier.safeDrawingPadding().padding(20.dp)) {
            QbxBrand()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.language_title),style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(LanguagePreferences.languages,key={it.first}) { language ->
                    OutlinedButton(onClick={onSelect(language.first)},modifier=Modifier.fillMaxWidth()) { Text(language.second) }
                }
            }
        }
    }
}
