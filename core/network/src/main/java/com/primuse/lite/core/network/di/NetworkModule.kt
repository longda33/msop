package com.primuse.lite.core.network.di

import com.primuse.lite.core.network.OpenListClient
import com.primuse.lite.core.network.StreamUrlResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** 播放层只认接口，不认 OkHttp 客户端，方便以后换回 Subsonic。 */
    @Binds
    @Singleton
    abstract fun bindStreamUrlResolver(client: OpenListClient): StreamUrlResolver
}
