package com.aggregator.shell.core.data

import com.aggregator.shell.core.data.local.entity.SubscriptionEntity

/**
 * High-level source management facade. Concrete per-module repositories are
 * in the same package; this is the top-level entry point used by UI ViewModels.
 */
interface SourceManager

/**
 * Per-module source repositories live under `com.aggregator.shell.core.data.source`.
 */
interface VideoSourceRepo {
    suspend fun upsertLocal(name: String, configJson: String)
    suspend fun importFromUrl(name: String, url: String)
    suspend fun clear()
}

interface ReaderSourceRepo {
    suspend fun upsertLocal(name: String, bookSourceJson: String)
    suspend fun importFromUrl(name: String, url: String)
}

interface MusicSourceRepo {
    suspend fun upsertLocal(name: String, script: String)
    suspend fun importFromUrl(name: String, url: String)
}

/**
 * Subscribes to remote source repositories. The shell ships with no bundled
 * source content; all content is user-imported. `update` performs a dedup-merge
 * pull; `listSubscriptions` returns the persisted subscription records.
 */
interface SubscriptionManager {
    suspend fun addSubscription(name: String, module: String, url: String)
    suspend fun update(subId: String)
    suspend fun remove(subId: String)
    suspend fun listSubscriptions(): List<SubscriptionEntity>
}
