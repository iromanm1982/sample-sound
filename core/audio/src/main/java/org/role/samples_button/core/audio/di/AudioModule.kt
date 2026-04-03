package org.role.samples_button.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.role.samples_button.core.audio.SoundPoolManager
import org.role.samples_button.core.audio.SoundPoolPlayer

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class AudioModule {
    @Binds
    @ActivityRetainedScoped
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer
}
