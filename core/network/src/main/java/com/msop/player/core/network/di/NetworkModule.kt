package com.msop.player.core.network.di

import com.msop.player.core.network.CompositeStreamUrlResolver
import com.msop.player.core.network.StreamUrlResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** 播放层只认接口，不认 OkHttp 客户端；内部按 scheme 分发给 OpenList / WebDAV。 */
    @Binds
    @Singleton
    abstract fun bindStreamUrlResolver(resolver: CompositeStreamUrlResolver): StreamUrlResolver
}
