package org.role.samples_button.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.role.samples_button.core.audio.SoundPoolManager
import org.role.samples_button.core.audio.SoundPoolPlayer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer
}
