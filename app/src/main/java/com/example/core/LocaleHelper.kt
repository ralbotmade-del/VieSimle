package com.example.core

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguageOption(
    val code: String,
    val nativeLabel: String,
    val flagOrCode: String
)

object AppLanguages {
    val ALL = listOf(
        AppLanguageOption("system", "Système", "AUTO"),
        AppLanguageOption("fr", "Français", "FR"),
        AppLanguageOption("en", "English", "EN"),
        AppLanguageOption("es", "Español", "ES")
    )
}

/**
 * Wraps the base Activity context while overriding only [getResources] and [getAssets]
 * with the selected locale's configuration. Because the base reference remains the real
 * [androidx.fragment.app.FragmentActivity], `Context.findFragmentActivity()` for
 * `BiometricPrompt` continues to resolve reliably.
 */
class LocalizedActivityContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
    override fun getAssets(): AssetManager = localizedResources.assets
}

object LocaleHelper {

    fun resolveTargetLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "fr" -> Locale.FRENCH
            "en" -> Locale.ENGLISH
            "es" -> Locale("es")
            else -> Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
        }
    }

    fun wrapContextWithLocale(baseContext: Context, languageCode: String): Pair<Context, Configuration> {
        val targetLocale = resolveTargetLocale(languageCode)
        Locale.setDefault(targetLocale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(targetLocale)
        config.setLocales(LocaleList(targetLocale))
        val localizedResources = baseContext.createConfigurationContext(config).resources
        val wrapped = LocalizedActivityContextWrapper(baseContext, localizedResources)
        return wrapped to config
    }

    fun applySystemAppLocale(context: Context, languageCode: String) {
        val targetLocale = resolveTargetLocale(languageCode)
        Locale.setDefault(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val lm = context.getSystemService(LocaleManager::class.java)
                val list = when (languageCode.lowercase()) {
                    "fr", "en", "es" -> LocaleList.forLanguageTags(languageCode.lowercase())
                    else -> LocaleList.getEmptyLocaleList()
                }
                lm?.applicationLocales = list
            }
        }
    }
}
