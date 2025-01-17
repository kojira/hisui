package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class BookmarkListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    companion object {
        const val kind = 30001

        fun addEvent(
            earlierVersion: BookmarkListEvent?,
            eventId: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) = addTag(earlierVersion, "e", eventId, isPrivate, signer, createdAt, onReady)

        fun addReplaceable(
            earlierVersion: BookmarkListEvent?,
            aTag: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) = addTag(earlierVersion, "a", aTag.toTag(), isPrivate, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: BookmarkListEvent?,
            tagName: String,
            tagValue: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) {
            add(
                earlierVersion,
                listOf(listOf(tagName, tagValue)),
                isPrivate,
                signer,
                createdAt,
                onReady
            )
        }

        fun add(
            earlierVersion: BookmarkListEvent?,
            listNewTags: List<List<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) {
            if (isPrivate) {
                if (earlierVersion != null) {
                    earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                        encryptTags(
                            privateTags = privateTags.plus(listNewTags),
                            signer = signer
                        ) { encryptedTags ->
                            create(
                                content = encryptedTags,
                                tags = earlierVersion.tags,
                                signer = signer,
                                createdAt = createdAt,
                                onReady = onReady
                            )
                        }
                    }
                } else {
                    encryptTags(
                        privateTags = listNewTags,
                        signer = signer
                    ) { encryptedTags ->
                        create(
                            content = encryptedTags,
                            tags = emptyList(),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
                        )
                    }
                }
            } else {
                create(
                    content = earlierVersion?.content ?: "",
                    tags = (earlierVersion?.tags ?: emptyList()).plus(listNewTags),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun removeEvent(
            earlierVersion: BookmarkListEvent,
            eventId: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) = removeTag(earlierVersion, "e", eventId, isPrivate, signer, createdAt, onReady)

        fun removeReplaceable(
            earlierVersion: BookmarkListEvent,
            aTag: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) = removeTag(earlierVersion, "a", aTag.toTag(), isPrivate, signer, createdAt, onReady)

        private fun removeTag(
            earlierVersion: BookmarkListEvent,
            tagName: String,
            tagValue: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags = privateTags.filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) },
                        signer = signer
                    ) { encryptedTags ->
                        create(
                            content = encryptedTags,
                            tags = earlierVersion.tags.filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) },
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
                        )
                    }
                }
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) },
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun create(
            content: String,
            tags: List<List<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) {
            signer.sign(createdAt, kind, tags, content, onReady)
        }

        fun create(
            name: String = "",

            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,

            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,

            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit
        ) {
            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", name))

            events?.forEach {
                tags.add(listOf("e", it))
            }
            users?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            createPrivateTags(privEvents, privUsers, privAddresses, signer) { content ->
                signer.sign(createdAt, kind, tags, content, onReady)
            }
        }
    }
}
