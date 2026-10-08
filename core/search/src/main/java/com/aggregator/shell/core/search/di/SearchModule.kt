package com.aggregator.shell.core.search.di

import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.search.SearchAggregator
import com.aggregator.shell.core.source.api.VideoEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    fun provideSearchAggregator(
        videoEngine: VideoEngine,
        searchHistoryDao: SearchHistoryDao
    ) = SearchAggregator(videoEngine) { module, keyword ->
        withContext(Dispatchers.IO) {
            searchHistoryDao.upsert(
                SearchHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    module = module,
                    keyword = keyword,
                    ts = System.currentTimeMillis()
                )
            )
        }
    }
}
