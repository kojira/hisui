package com.vitorpamplona.quartz.signers

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.LnZapRequestEvent

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT
}

class ExternalSignerLauncher(
    private val npub: String,
    private val signerPackageName: String = "com.greenart7c3.nostrsigner"
) {
    private val contentCache = LruCache<String, (String) -> Unit>(20)

    private var signerAppLauncher: ((Intent) -> Unit)? = null
    private var contentResolver: (() -> ContentResolver)? = null

    /**
     * Call this function when the launcher becomes available on activity, fragment or compose
     */
    fun registerLauncher(
        launcher: ((Intent) -> Unit),
        contentResolver: (() -> ContentResolver),
    ) {
        this.signerAppLauncher = launcher
        this.contentResolver = contentResolver
    }

    /**
     * Call this function when the activity is destroyed or is about to be replaced.
     */
    fun clearLauncher() {
        this.signerAppLauncher = null
        this.contentResolver = null
    }

    fun newResult(data: Intent) {
        val signature = data.getStringExtra("signature") ?: ""
        val id = data.getStringExtra("id") ?: ""
        if (id.isNotBlank()) {
            contentCache.get(id)?.invoke(signature)
        }
    }


    fun openSignerApp(
        data: String,
        type: SignerType,
        pubKey: HexKey,
        id: String,
        onReady: (String)-> Unit
    ) {
        signerAppLauncher?.let {
            openSignerApp(
                data, type, it, pubKey, id, onReady
            )
        }
    }

    private fun openSignerApp(
        data: String,
        type: SignerType,
        intentLauncher: (Intent) -> Unit,
        pubKey: HexKey,
        id: String,
        onReady: (String)-> Unit
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$data"))
        val signerType = when (type) {
            SignerType.SIGN_EVENT -> "sign_event"
            SignerType.NIP04_ENCRYPT -> "nip04_encrypt"
            SignerType.NIP04_DECRYPT -> "nip04_decrypt"
            SignerType.NIP44_ENCRYPT -> "nip44_encrypt"
            SignerType.NIP44_DECRYPT -> "nip44_decrypt"
            SignerType.GET_PUBLIC_KEY -> "get_public_key"
            SignerType.DECRYPT_ZAP_EVENT -> "decrypt_zap_event"
        }
        intent.putExtra("type", signerType)
        intent.putExtra("pubKey", pubKey)
        intent.putExtra("id", id)
        if (type !== SignerType.GET_PUBLIC_KEY) {
            intent.putExtra("current_user", npub)
        }
        intent.`package` = signerPackageName

        contentCache.put(id, onReady)

        intentLauncher(intent)
    }

    fun openSigner(event: EventInterface, columnName: String = "signature", onReady: (String)-> Unit) {
        val result = getDataFromResolver(SignerType.SIGN_EVENT, arrayOf(event.toJson(), event.pubKey()), columnName)
        if (result == null) {
            openSignerApp(
                event.toJson(),
                SignerType.SIGN_EVENT,
                "",
                event.id(),
                onReady
            )
        } else {
            onReady(result)
        }
    }

    fun getDataFromResolver(signerType: SignerType, data: Array<out String>, columnName: String = "signature"): String? {
        return contentResolver?.let { it() }?.let {
            getDataFromResolver(signerType, data, columnName, it)
        }
    }

    fun getDataFromResolver(signerType: SignerType, data: Array<out String>, columnName: String = "signature", contentResolver: ContentResolver): String? {
        val localData = if (signerType !== SignerType.GET_PUBLIC_KEY) {
            data.toList().plus(npub).toTypedArray()
        } else {
            data
        }

        contentResolver.query(
            Uri.parse("content://${signerPackageName}.$signerType"),
            localData,
            null,
            null,
            null
        ).use {
            if (it == null) {
                return null
            }
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(columnName)
                if (index < 0) {
                    Log.d("getDataFromResolver", "column '$columnName' not found")
                    return null
                }
                return it.getString(index)
            }
        }
        return null
    }

    fun decrypt(encryptedContent: String, pubKey: HexKey, signerType: SignerType = SignerType.NIP04_DECRYPT, onReady: (String)-> Unit) {
        val id = (encryptedContent + pubKey + onReady.toString()).hashCode().toString()
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result == null) {
            openSignerApp(
                encryptedContent,
                signerType,
                pubKey,
                id,
                onReady
            )
        } else {
            onReady(result)
        }
    }

    fun encrypt(decryptedContent: String, pubKey: HexKey, signerType: SignerType = SignerType.NIP04_ENCRYPT, onReady: (String)-> Unit) {
        val id = (decryptedContent + pubKey + onReady.toString()).hashCode().toString()
        val result = getDataFromResolver(signerType, arrayOf(decryptedContent, pubKey))
        if (result == null) {
            openSignerApp(
                decryptedContent,
                signerType,
                pubKey,
                id,
                onReady
            )
        } else {
            onReady(result)
        }
    }

    fun decryptZapEvent(event: LnZapRequestEvent, onReady: (String)-> Unit) {
        val result = getDataFromResolver(SignerType.DECRYPT_ZAP_EVENT, arrayOf(event.toJson(), event.pubKey))
        if (result == null) {
            openSignerApp(
                event.toJson(),
                SignerType.DECRYPT_ZAP_EVENT,
                event.pubKey,
                event.id,
                onReady
            )
        } else {
            onReady(result)
        }
    }
}
