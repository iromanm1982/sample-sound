# Feature File Browser — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir al usuario explorar los archivos de audio del dispositivo y agregarlos a grupos como SoundButtons, visibles en un grid de 3 columnas dentro de cada GroupCard.

**Architecture:** `feature/browser` (api + impl) provee `FileBrowserScreen` + `FileBrowserViewModel`. La capa de datos en `:core:data` expone `AudioFileRepository` (MediaStore) y `SoundButtonRepositoryImpl` (Room). `GroupRepositoryImpl` se actualiza para devolver grupos con sus botones reales via `flatMapLatest + combine`. Navegación con `navigation-compose` en `MainActivity`.

**Tech Stack:** Kotlin 2.1.20, AGP 9.1.0, Hilt 2.56.1 (sin Gradle plugin — AGP 9.x incompatible), Room 2.7.0, Compose BOM 2025.04.00, navigation-compose 2.9.0, accompanist-permissions 0.37.3, KSP 2.1.20-1.0.32

**IMPORTANTE — quirk AGP 9.1.0:** NO usar `alias(libs.plugins.hilt.android)` en ningún módulo. Para `@HiltViewModel`: solo `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.android)`. Para `@HiltAndroidApp`/`@AndroidEntryPoint` en `:app`: especificar valor en anotación (`@HiltAndroidApp(Application::class)`) y extender manualmente `Hilt_*`.

---

## File Map

**Modificados:**
- `gradle/libs.versions.toml` — añadir navigationCompose, accompanist, hilt-navigation-compose
- `settings.gradle.kts` — registrar :feature:browser:api y :feature:browser:impl
- `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt` — completar esqueleto
- `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt` — cambiar firma addButton
- `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt` — inyectar SoundButtonDao, real join
- `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt` — añadir provideSoundButtonDao
- `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt` — añadir SoundButtonRepository, AudioFileRepository
- `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt` — actualizar constructor + test nuevo
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt` — GroupCard con grid
- `app/src/main/java/org/role/samples_button/MainActivity.kt` — reescribir con NavHost
- `app/src/main/AndroidManifest.xml` — añadir permisos audio
- `app/build.gradle.kts` — añadir navigation-compose, hilt-navigation-compose, feature:browser:impl

**Creados:**
- `core/model/src/main/java/org/role/samples_button/core/model/AudioFile.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/AudioFileRepository.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt`
- `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt`
- `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt`
- `feature/browser/api/build.gradle.kts`
- `feature/browser/api/src/main/java/org/role/samples_button/feature/browser/api/FileBrowserNavigator.kt`
- `feature/browser/impl/build.gradle.kts`
- `feature/browser/impl/src/main/AndroidManifest.xml`
- `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModel.kt`
- `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserScreen.kt`
- `feature/browser/impl/src/test/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModelTest.kt`

---

## Task 1: Catalog + settings

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Añadir versiones y librerías al catálogo**

En `gradle/libs.versions.toml`, añadir en `[versions]` después de `activityCompose = "1.10.1"`:

```toml
navigationCompose = "2.9.0"
accompanist = "0.37.3"
```

En `[libraries]` al final (antes de `[plugins]`):

```toml
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanist" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
```

- [ ] **Step 2: Registrar módulos feature en settings.gradle.kts**

Añadir al final de `settings.gradle.kts`:

```kotlin
include(":feature:browser:api")
include(":feature:browser:impl")
```

- [ ] **Step 3: Verificar**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew projects 2>&1 | grep browser
```

Expected: muestra `:feature:browser:api` y `:feature:browser:impl`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts
git commit -m "build: add navigation-compose, accompanist, hilt-nav-compose; register browser modules"
```

---

## Task 2: AudioFile model + SoundButtonDao

**Files:**
- Create: `core/model/src/main/java/org/role/samples_button/core/model/AudioFile.kt`
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`

- [ ] **Step 1: Crear AudioFile domain model**

Crear `core/model/src/main/java/org/role/samples_button/core/model/AudioFile.kt`:

```kotlin
package org.role.samples_button.core.model

data class AudioFile(
    val id: Long,
    val displayName: String,
    val filePath: String
)
```

- [ ] **Step 2: Completar SoundButtonDao**

Reemplazar el contenido de `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt` (actualmente un esqueleto vacío):

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundButtonDao {
    @Query("SELECT * FROM sound_buttons WHERE groupId = :groupId ORDER BY position ASC")
    fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>>

    @Insert
    suspend fun insert(button: SoundButtonEntity): Long

    @Query("DELETE FROM sound_buttons WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 3: Verificar que :core:database compila**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:database:assemble 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Verificar que :core:model compila**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:model:assemble 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/model/src/ core/database/src/
git commit -m "feat: add AudioFile model and complete SoundButtonDao"
```

---

## Task 3: SoundButtonRepository + AudioFileRepository (TDD)

**Files:**
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/AudioFileRepository.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`
- Create: `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt`
- Create: `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt`

- [ ] **Step 1: Actualizar interfaz SoundButtonRepository**

Reemplazar `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`:

```kotlin
package org.role.samples_button.core.data

interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long)
    suspend fun deleteButton(id: Long)
}
```

- [ ] **Step 2: Crear AudioFileRepository**

Crear `core/data/src/main/java/org/role/samples_button/core/data/AudioFileRepository.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.AudioFile

interface AudioFileRepository {
    fun getAudioFiles(): Flow<List<AudioFile>>
}
```

- [ ] **Step 3: Crear FakeSoundButtonDao**

Crear `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity

class FakeSoundButtonDao : SoundButtonDao {
    private val map = mutableMapOf<Long, MutableList<SoundButtonEntity>>()
    private val flows = mutableMapOf<Long, MutableStateFlow<List<SoundButtonEntity>>>()
    private var nextId = 1L

    private fun flowFor(groupId: Long): MutableStateFlow<List<SoundButtonEntity>> =
        flows.getOrPut(groupId) { MutableStateFlow(emptyList()) }

    override fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>> = flowFor(groupId)

    override suspend fun insert(button: SoundButtonEntity): Long {
        val entity = button.copy(id = nextId++)
        map.getOrPut(entity.groupId) { mutableListOf() }.add(entity)
        flowFor(entity.groupId).value = map[entity.groupId]!!.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        map.forEach { (groupId, list) ->
            if (list.removeAll { it.id == id }) {
                flowFor(groupId).value = list.toList()
            }
        }
    }
}
```

- [ ] **Step 4: Escribir test SoundButtonRepositoryImplTest**

Crear `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundButtonRepositoryImplTest {

    @Test
    fun `addButton inserts button with correct data`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
        val buttons = dao.getByGroupId(1L).first()
        assertEquals(1, buttons.size)
        assertEquals("Kick", buttons[0].label)
        assertEquals("/sdcard/kick.mp3", buttons[0].filePath)
        assertEquals(1L, buttons[0].groupId)
        assertEquals(0, buttons[0].position)
    }

    @Test
    fun `addButton assigns sequential positions within same group`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("First", "/sdcard/a.mp3", 1L)
        repo.addButton("Second", "/sdcard/b.mp3", 1L)
        val buttons = dao.getByGroupId(1L).first()
        assertEquals(0, buttons[0].position)
        assertEquals(1, buttons[1].position)
    }

    @Test
    fun `deleteButton removes button from dao`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
        val id = dao.getByGroupId(1L).first()[0].id
        repo.deleteButton(id)
        assertTrue(dao.getByGroupId(1L).first().isEmpty())
    }
}
```

- [ ] **Step 5: Verificar que el test falla (SoundButtonRepositoryImpl no existe)**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:data:testDebugUnitTest 2>&1 | grep -E "error:|FAILED|BUILD" | head -5
```

Expected: BUILD FAILED — `SoundButtonRepositoryImpl` not found

- [ ] **Step 6: Crear SoundButtonRepositoryImpl**

Crear `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.first
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundButtonRepositoryImpl @Inject constructor(
    private val soundButtonDao: SoundButtonDao
) : SoundButtonRepository {

    override suspend fun addButton(label: String, filePath: String, groupId: Long) {
        val position = soundButtonDao.getByGroupId(groupId).first().size
        soundButtonDao.insert(
            SoundButtonEntity(label = label, filePath = filePath, groupId = groupId, position = position)
        )
    }

    override suspend fun deleteButton(id: Long) {
        soundButtonDao.deleteById(id)
    }
}
```

- [ ] **Step 7: Crear AudioFileRepositoryImpl**

Crear `core/data/src/main/java/org/role/samples_button/core/data/AudioFileRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.role.samples_button.core.model.AudioFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioFileRepository {

    override fun getAudioFiles(): Flow<List<AudioFile>> = flow {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )
        val files = mutableListOf<AudioFile>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val displayName = it.getString(nameCol).substringBeforeLast(".")
                files += AudioFile(it.getLong(idCol), displayName, it.getString(dataCol))
            }
        }
        emit(files)
    }
}
```

- [ ] **Step 8: Actualizar DatabaseModule — añadir SoundButtonDao**

Reemplazar `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt`:

```kotlin
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
```

- [ ] **Step 9: Actualizar RepositoryModule — añadir SoundButtonRepository y AudioFileRepository**

Reemplazar `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`:

```kotlin
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
```

- [ ] **Step 10: Verificar que los tests pasan**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:data:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL` — los 3 tests de SoundButtonRepositoryImplTest pasan, más los 3 existentes de GroupRepositoryImplTest

- [ ] **Step 11: Commit**

```bash
git add core/data/src/
git commit -m "feat: add SoundButtonRepositoryImpl, AudioFileRepository, and unit tests in :core:data"
```

---

## Task 4: Actualizar GroupRepositoryImpl (join real con botones)

**Files:**
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`

- [ ] **Step 1: Escribir test nuevo para getGroupsWithButtons con botones**

Reemplazar `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt` completo:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.role.samples_button.core.database.SoundButtonEntity

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryImplTest {

    @Test
    fun `createGroup inserts group with correct name`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Drums")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups.size)
        assertEquals("Drums", groups[0].name)
    }

    @Test
    fun `deleteGroup removes group from list`() = runTest {
        val dao = FakeGroupDao()
        val repo = GroupRepositoryImpl(dao, FakeSoundButtonDao())
        repo.createGroup("Drums")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.deleteGroup(id)
        assertTrue(repo.getGroupsWithButtons().first().isEmpty())
    }

    @Test
    fun `createGroup assigns sequential positions`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("First")
        repo.createGroup("Second")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(0, groups[0].position)
        assertEquals(1, groups[1].position)
    }

    @Test
    fun `getGroupsWithButtons includes buttons for each group`() = runTest {
        val groupDao = FakeGroupDao()
        val buttonDao = FakeSoundButtonDao()
        val repo = GroupRepositoryImpl(groupDao, buttonDao)
        repo.createGroup("Drums")
        val groupId = repo.getGroupsWithButtons().first()[0].id
        buttonDao.insert(
            SoundButtonEntity(label = "Kick", filePath = "/kick.mp3", groupId = groupId, position = 0)
        )
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups[0].buttons.size)
        assertEquals("Kick", groups[0].buttons[0].label)
    }
}
```

- [ ] **Step 2: Verificar que el nuevo test falla (GroupRepositoryImpl aún usa emptyList)**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:data:testDebugUnitTest 2>&1 | grep -E "FAILED|tests were run|error:" | head -5
```

Expected: BUILD FAILED — el test `getGroupsWithButtons includes buttons` falla, más posiblemente error de compilación porque `GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())` no compila (constructor actual solo toma groupDao)

- [ ] **Step 3: Reemplazar GroupRepositoryImpl con join real**

Reemplazar `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val soundButtonDao: SoundButtonDao
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val buttonFlows = groupEntities.map { groupEntity ->
                    soundButtonDao.getByGroupId(groupEntity.id).map { buttons ->
                        Group(
                            id = groupEntity.id,
                            name = groupEntity.name,
                            position = groupEntity.position,
                            buttons = buttons.map {
                                SoundButton(it.id, it.label, it.filePath, it.groupId, it.position)
                            }
                        )
                    }
                }
                combine(buttonFlows) { it.toList() }
            }
        }

    override suspend fun createGroup(name: String) {
        val currentSize = groupDao.getAllGroups().first().size
        groupDao.insert(GroupEntity(name = name, position = currentSize))
    }

    override suspend fun deleteGroup(id: Long) {
        groupDao.deleteById(id)
    }

    override suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>) = Unit
}
```

- [ ] **Step 4: Verificar que todos los tests de :core:data pasan**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:data:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL` — 7 tests pasan (3 GroupRepositoryImplTest + 1 nuevo + 3 SoundButtonRepositoryImplTest)

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt
git add core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt
git commit -m "feat: update GroupRepositoryImpl to join real buttons via SoundButtonDao"
```

---

## Task 5: feature:browser (api + impl, TDD ViewModel)

**Files:**
- Create: `feature/browser/api/build.gradle.kts`
- Create: `feature/browser/api/src/main/java/org/role/samples_button/feature/browser/api/FileBrowserNavigator.kt`
- Create: `feature/browser/impl/build.gradle.kts`
- Create: `feature/browser/impl/src/main/AndroidManifest.xml`
- Create: `feature/browser/impl/src/test/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModelTest.kt`
- Create: `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModel.kt`
- Create: `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserScreen.kt`

- [ ] **Step 1: Crear feature/browser/api/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}
```

- [ ] **Step 2: Crear FileBrowserNavigator.kt**

Crear `feature/browser/api/src/main/java/org/role/samples_button/feature/browser/api/FileBrowserNavigator.kt`:

```kotlin
package org.role.samples_button.feature.browser.api

interface FileBrowserNavigator {
    fun navigateToFileBrowser(groupId: Long)
    fun navigateBack()
}
```

- [ ] **Step 3: Crear feature/browser/impl/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.feature.browser.impl"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:browser:api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.accompanist.permissions)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4: Crear AndroidManifest.xml para el módulo impl**

Crear `feature/browser/impl/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 5: Escribir test FileBrowserViewModelTest (antes de implementar el VM)**

Crear `feature/browser/impl/src/test/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModelTest.kt`:

```kotlin
package org.role.samples_button.feature.browser.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.AudioFileRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.AudioFile

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial audioFiles state is empty list`() = runTest {
        val viewModel = FileBrowserViewModel(FakeAudioFileRepository(), FakeSoundButtonRepository())
        assertEquals(emptyList<AudioFile>(), viewModel.audioFiles.value)
    }

    @Test
    fun `addButtonToGroup delegates to repository with correct params`() = runTest {
        val repo = FakeSoundButtonRepository()
        val viewModel = FileBrowserViewModel(FakeAudioFileRepository(), repo)
        viewModel.addButtonToGroup("Kick", "/sdcard/kick.mp3", 1L)
        assertEquals(1, repo.addedButtons.size)
        assertEquals("Kick", repo.addedButtons[0].first)
        assertEquals("/sdcard/kick.mp3", repo.addedButtons[0].second)
        assertEquals(1L, repo.addedButtons[0].third)
    }
}

class FakeAudioFileRepository : AudioFileRepository {
    override fun getAudioFiles(): Flow<List<AudioFile>> = flowOf(emptyList())
}

class FakeSoundButtonRepository : SoundButtonRepository {
    val addedButtons = mutableListOf<Triple<String, String, Long>>()
    val deletedIds = mutableListOf<Long>()

    override suspend fun addButton(label: String, filePath: String, groupId: Long) {
        addedButtons.add(Triple(label, filePath, groupId))
    }

    override suspend fun deleteButton(id: Long) {
        deletedIds.add(id)
    }
}
```

- [ ] **Step 6: Verificar que el test falla (FileBrowserViewModel no existe)**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:browser:impl:testDebugUnitTest 2>&1 | grep -E "FAILED|error:" | head -5
```

Expected: BUILD FAILED — `FileBrowserViewModel` not found

- [ ] **Step 7: Crear FileBrowserViewModel**

Crear `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserViewModel.kt`:

```kotlin
package org.role.samples_button.feature.browser.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.AudioFileRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.AudioFile
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val audioFileRepository: AudioFileRepository,
    private val soundButtonRepository: SoundButtonRepository
) : ViewModel() {

    val audioFiles: StateFlow<List<AudioFile>> = audioFileRepository
        .getAudioFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addButtonToGroup(label: String, filePath: String, groupId: Long) {
        viewModelScope.launch {
            soundButtonRepository.addButton(label.trim(), filePath, groupId)
        }
    }
}
```

- [ ] **Step 8: Verificar que los tests de FileBrowserViewModel pasan**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:browser:impl:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL` — 2 tests pasan

- [ ] **Step 9: Crear FileBrowserScreen**

Crear `feature/browser/impl/src/main/java/org/role/samples_button/feature/browser/impl/FileBrowserScreen.kt`:

```kotlin
package org.role.samples_button.feature.browser.impl

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import org.role.samples_button.core.model.AudioFile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FileBrowserScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
    var selectedFile by remember { mutableStateOf<AudioFile?>(null) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar audio") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            permissionState.status.isGranted -> {
                if (audioFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron archivos de audio")
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                        items(audioFiles, key = { it.id }) { audioFile ->
                            AudioFileItem(
                                audioFile = audioFile,
                                onClick = { selectedFile = audioFile }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            permissionState.status.shouldShowRationale -> {
                PermissionRequired(
                    message = "Se necesita acceso a tus archivos de audio para agregar sonidos.",
                    buttonText = "Conceder permiso",
                    onRequest = { permissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                PermissionRequired(
                    message = "Se necesita acceso a tus archivos de audio para agregar sonidos.",
                    buttonText = "Conceder permiso",
                    onRequest = { permissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    selectedFile?.let { file ->
        ConfirmLabelDialog(
            defaultLabel = file.displayName,
            onConfirm = { label ->
                viewModel.addButtonToGroup(label, file.filePath, groupId)
                selectedFile = null
                onNavigateBack()
            },
            onDismiss = { selectedFile = null }
        )
    }
}

@Composable
private fun AudioFileItem(audioFile: AudioFile, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(audioFile.displayName) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PermissionRequired(
    message: String,
    buttonText: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(message)
            TextButton(onClick = onRequest) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ConfirmLabelDialog(
    defaultLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(defaultLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del botón") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onConfirm(label.trim()) },
                enabled = label.isNotBlank()
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 10: Verificar que :feature:browser:impl:assembleDebug compila**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:browser:impl:assembleDebug 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

```bash
git add feature/browser/
git commit -m "feat: add :feature:browser with FileBrowserScreen, ViewModel, and tests"
```

---

## Task 6: Actualizar SoundBoardScreen (GroupCard grid)

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Reemplazar SoundBoardScreen.kt con grid de botones**

Reemplazar el contenido completo de `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(
    viewModel: SoundBoardViewModel,
    onNavigateToFileBrowser: (Long) -> Unit = {}
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SoundBoard") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear grupo")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            GroupList(
                groups = groups,
                onDelete = { viewModel.deleteGroup(it) },
                onAddSound = { groupId -> onNavigateToFileBrowser(groupId) },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sin grupos todavía")
            Text("Toca + para crear uno")
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    onDelete: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                onDelete = { onDelete(group.id) },
                onAddSound = { onAddSound(group.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onDelete: () -> Unit,
    onAddSound: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${group.name}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ButtonGrid(buttons = group.buttons, onAddSound = onAddSound)
        }
    }
}

@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    onAddSound: () -> Unit
) {
    val allItems: List<SoundButton?> = buttons + listOf(null) // null = add button slot
    allItems.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { button ->
                if (button != null) {
                    SoundButtonItem(button = button, modifier = Modifier.weight(1f))
                } else {
                    AddSoundButton(onClick = onAddSound, modifier = Modifier.weight(1f))
                }
            }
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SoundButtonItem(button: SoundButton, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(64.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
private fun AddSoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(64.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Agregar sonido")
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del grupo") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Verificar que :feature:soundboard:impl compila y tests siguen pasando**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:soundboard:impl:assembleDebug :feature:soundboard:impl:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: update GroupCard with 3-column button grid and add-sound button"
```

---

## Task 7: Wire :app (NavHost + permisos)

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/org/role/samples_button/MainActivity.kt`

- [ ] **Step 1: Actualizar app/build.gradle.kts**

Reemplazar el contenido completo de `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.role.samples_button"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(project(":feature:soundboard:impl"))
    implementation(project(":feature:browser:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 2: Añadir permisos de audio a AndroidManifest.xml**

Reemplazar `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application
        android:name=".SamplesButtonApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Samplesbutton"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Samplesbutton">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Reescribir MainActivity con NavHost**

Reemplazar `app/src/main/java/org/role/samples_button/MainActivity.kt`:

```kotlin
package org.role.samples_button

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import org.role.samples_button.core.designsystem.SamplesButtonTheme
import org.role.samples_button.feature.browser.impl.FileBrowserScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardViewModel

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "soundboard") {
                    composable("soundboard") {
                        val viewModel: SoundBoardViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        SoundBoardScreen(
                            viewModel = viewModel,
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            }
                        )
                    }
                    composable(
                        route = "file_browser/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments!!.getLong("groupId")
                        FileBrowserScreen(
                            groupId = groupId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compilar debug completo**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

Si hay error de compilación:
- `Unresolved reference: hiltViewModel` → verificar que `hilt-navigation-compose` está en las deps de `:app` y `:feature:browser:impl`
- `Unresolved reference: NavHost` → verificar que `navigation-compose` está en `:app`
- `Unresolved reference: FileBrowserScreen` → verificar que `:feature:browser:impl` está en deps de `:app`

- [ ] **Step 5: Commit**

```bash
git add app/
git commit -m "feat: wire NavHost navigation with FileBrowserScreen in :app"
```

---

## Task 8: Instalar y verificar en emulador

- [ ] **Step 1: Verificar emulador conectado**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

Expected: `emulator-XXXX   device`

- [ ] **Step 2: Instalar en emulador**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew installDebug 2>&1 | tail -5
```

Expected: `Installed on 1 device.` + `BUILD SUCCESSFUL`

- [ ] **Step 3: Lanzar app**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n "org.role.samples_button/.MainActivity"
```

Expected: `Starting: Intent { cmp=org.role.samples_button/.MainActivity }`

- [ ] **Step 4: Verificar proceso corriendo sin crashes**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell pidof org.role.samples_button
```

Expected: número de PID

Si vacío (crash), obtener logs:
```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d -s AndroidRuntime:E 2>&1 | tail -30
```

- [ ] **Step 5: Verificar flujo completo manualmente en el emulador**

En el emulador verificar:
1. Se ve "SoundBoard" en TopAppBar con FAB "+"
2. Crear un grupo con el FAB → aparece GroupCard con botón "+"
3. Tocar "+" en el GroupCard → navega a "Seleccionar audio"
4. Si pide permiso → concederlo con "Conceder permiso"
5. Se ve lista de archivos de audio del dispositivo
6. Tocar un archivo → aparece dialog con label pre-llenado
7. Confirmar → regresa a SoundBoard
8. El GroupCard muestra el nuevo botón en el grid

- [ ] **Step 6: Commit si hubo correcciones**

Si se realizaron cambios para resolver errores de runtime:

```bash
git add -A
git commit -m "fix: resolve runtime issues in file browser navigation"
```
