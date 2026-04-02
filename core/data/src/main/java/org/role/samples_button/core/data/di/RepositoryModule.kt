package org.role.samples_button.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.role.samples_button.core.data.AudioFileRepository
import org.role.samples_button.core.data.AudioFileRepositoryImpl
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.GroupRepositoryImpl
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.data.SoundButtonRepositoryImpl
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
}
