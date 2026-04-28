package org.role.audio_group.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.role.audio_group.core.data.AudioFileRepository
import org.role.audio_group.core.data.AudioFileRepositoryImpl
import org.role.audio_group.core.data.GroupRepository
import org.role.audio_group.core.data.GroupRepositoryImpl
import org.role.audio_group.core.data.SoundButtonRepository
import org.role.audio_group.core.data.SoundButtonRepositoryImpl
import org.role.audio_group.core.data.UserPreferencesRepository
import org.role.audio_group.core.data.UserPreferencesRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindSoundButtonRepository(impl: SoundButtonRepositoryImpl): SoundButtonRepository

    @Binds
    @Singleton
    abstract fun bindAudioFileRepository(impl: AudioFileRepositoryImpl): AudioFileRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
