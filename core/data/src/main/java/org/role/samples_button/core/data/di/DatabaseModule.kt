package org.role.samples_button.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.role.samples_button.core.database.AppDatabase
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.SoundButtonDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "soundboard.db").build()

    @Provides
    fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideSoundButtonDao(db: AppDatabase): SoundButtonDao = db.soundButtonDao()
}
