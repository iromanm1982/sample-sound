package org.role.audio_group.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.role.audio_group.core.audio.DurationReader
import org.role.audio_group.core.audio.MediaMetadataDurationReader
import org.role.audio_group.core.audio.SoundPoolManager
import org.role.audio_group.core.audio.SoundPoolPlayer

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class AudioModule {

    @Binds
    @ActivityRetainedScoped
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer

    @Binds
    @ActivityRetainedScoped
    abstract fun bindDurationReader(impl: MediaMetadataDurationReader): DurationReader
}
