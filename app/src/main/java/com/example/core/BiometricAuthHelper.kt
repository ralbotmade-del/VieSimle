package com.example.core

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricCapability {
    AVAILABLE_BIOMETRIC,
    AVAILABLE_DEVICE_CREDENTIAL,
    NONE_ENROLLED,
    UNAVAILABLE
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

object BiometricAuthHelper {

    fun checkCapability(context: Context): BiometricCapability {
        val biometricManager = BiometricManager.from(context)
        val biometricResult = biometricManager.canAuthenticate(
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
        )
        if (biometricResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.AVAILABLE_BIOMETRIC
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val deviceCredResult = biometricManager.canAuthenticate(Authenticators.DEVICE_CREDENTIAL)
            if (deviceCredResult == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL
            }
        } else {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguard?.isDeviceSecure == true) {
                return BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL
            }
        }

        return if (biometricResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            BiometricCapability.NONE_ENROLLED
        } else {
            BiometricCapability.UNAVAILABLE
        }
    }

    fun capabilityLabel(capability: BiometricCapability): String = when (capability) {
        BiometricCapability.AVAILABLE_BIOMETRIC ->
            "Empreinte digitale ou reconnaissance faciale prête."
        BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL ->
            "Aucune empreinte enregistrée ; le code ou schéma de l’appareil peut être utilisé."
        BiometricCapability.NONE_ENROLLED ->
            "Aucune empreinte ni verrouillage d’écran configuré sur cet appareil."
        BiometricCapability.UNAVAILABLE ->
            "Capteur biométrique indisponible sur cet appareil."
    }

    fun buildBiometricEnrollIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    Authenticators.BIOMETRIC_STRONG or
                        Authenticators.BIOMETRIC_WEAK or
                        Authenticators.DEVICE_CREDENTIAL
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun launchSensitiveDocumentPrompt(
        context: Context,
        documentTitle: String,
        hasPinFallback: Boolean,
        onSuccess: (authMethodLabel: String) -> Unit,
        onUsePinFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        val safeTitle = documentTitle.trim().take(48).ifBlank { "Document sensible" }
        launchBiometricPrompt(
            context = context,
            hasPinFallback = hasPinFallback,
            title = "Accès au document sensible",
            subtitle = "Authentifiez-vous par empreinte ou reconnaissance faciale pour ouvrir « $safeTitle »",
            negativeButtonText = if (hasPinFallback) "Utiliser le code PIN" else "Annuler",
            onSuccess = onSuccess,
            onUsePinFallback = onUsePinFallback,
            onError = onError
        )
    }

    fun launchBiometricPrompt(
        context: Context,
        hasPinFallback: Boolean,
        title: String = "Déverrouiller VieSimple",
        subtitle: String = "Confirmez votre identité avant d’accéder au tableau de bord",
        negativeButtonText: String = if (hasPinFallback) "Utiliser le code PIN" else "Annuler",
        onSuccess: (authMethodLabel: String) -> Unit,
        onUsePinFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onError("Impossible d’ouvrir la fenêtre biométrique depuis ce contexte.")
            return
        }

        val capability = checkCapability(activity)
        val useDeviceCredentialFallback =
            !hasPinFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (capability == BiometricCapability.AVAILABLE_BIOMETRIC ||
                    capability == BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL)

        if (capability == BiometricCapability.NONE_ENROLLED ||
            capability == BiometricCapability.UNAVAILABLE ||
            (capability == BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        ) {
            onError(capabilityLabel(capability))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
                    ) {
                        "Verrouillage appareil"
                    } else {
                        "Biométrie"
                    }
                    onSuccess(label)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Empreinte ou biométrie non reconnue. Veuillez réessayer.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            if (hasPinFallback) onUsePinFallback()
                            else onError("Authentification annulée.")
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> {
                            onError("Authentification biométrique annulée. Appuyez pour réessayer.")
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onError("Trop de tentatives. Utilisez votre code PIN ou réessayez plus tard.")
                        }
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            onError("Aucune empreinte ni code appareil configuré.")
                        }
                        else -> {
                            onError(errString.toString().ifBlank { "Échec de la vérification biométrique." })
                        }
                    }
                }
            }
        )

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)

        if (useDeviceCredentialFallback) {
            promptBuilder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or
                    Authenticators.BIOMETRIC_WEAK or
                    Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            promptBuilder
                .setAllowedAuthenticators(
                    Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
                )
                .setNegativeButtonText(negativeButtonText)
        }

        try {
            prompt.authenticate(promptBuilder.build())
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Erreur lors du lancement de l’authentification biométrique.")
        }
    }
}
