# Feature Soundboard MVP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar `feature/soundboard` MVP: pantalla principal con lista de grupos, crear/eliminar grupos, persistencia en Room, Hilt DI completo, app arrancable en emulador.

**Architecture:** MVVM + Clean Architecture. `SoundBoardViewModel` obtiene `Flow<List<Group>>` del `GroupRepository`, expone `StateFlow` a la UI Compose. `GroupRepositoryImpl` mapea `GroupEntity` (Room) a `Group` (domain model). Hilt provee Room, DAOs y el Repository en `SingletonComponent`.

**Tech Stack:** Kotlin 2.1.20, AGP 9.1.0, Hilt 2.56.1, Room 2.7.0, Compose BOM 2025.04.00, Coroutines 1.10.2, KSP 2.1.20-1.0.32

---

## File Map

**Modificados:**
- `gradle/libs.versions.toml` — añadir activityCompose version + 3 librerías nuevas
- `settings.gradle.kts` — añadir 2 módulos feature
- `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt` — reemplazar esqueleto con queries reales
- `core/data/build.gradle.kts` — añadir hilt, ksp, coroutines-test
- `app/build.gradle.kts` — añadir hilt, ksp, compose.compiler, activity-compose, feature dep
- `app/src/main/AndroidManifest.xml` — añadir App class + Activity launcher

**Creados:**
- `feature/soundboard/api/build.gradle.kts`
- `feature/soundboard/api/src/main/java/org/role/samples_button/feature/soundboard/api/SoundBoardNavigator.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`
- `core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt`
- `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`
- `feature/soundboard/impl/build.gradle.kts`
- `feature/soundboard/impl/src/main/AndroidManifest.xml`
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`
- `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`
- `app/src/main/java/org/role/samples_button/SamplesButtonApp.kt`
- `app/src/main/java/org/role/samples_button/MainActivity.kt`

---

## Task 1: Catalog Updates + Module Registration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Añadir entradas al catálogo**

En `gradle/libs.versions.toml`, añadir en `[versions]` después de `lifecycle = "2.9.0"`:

```toml
activityCompose = "1.10.1"
```

En `[libraries]` al final de la sección:

```toml
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: Registrar los módulos feature en `settings.gradle.kts`**

Añadir al final del archivo (después de los `include(":core:*")`):

```kotlin
include(":feature:soundboard:api")
include(":feature:soundboard:impl")
```

- [ ] **Step 3: Verificar**

Run: `./gradlew projects`
Expected: muestra `:feature:soundboard:api` y `:feature:soundboard:impl` en la jerarquía

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts
git commit -m "build: register feature:soundboard modules and add missing catalog entries"
```

---

## Task 2: Create :feature:soundboard:api

**Files:**
- Create: `feature/soundboard/api/build.gradle.kts`
- Create: `feature/soundboard/api/src/main/java/org/role/samples_button/feature/soundboard/api/SoundBoardNavigator.kt`

- [ ] **Step 1: Crear `feature/soundboard/api/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}
```

- [ ] **Step 2: Crear `SoundBoardNavigator.kt`**

Path: `feature/soundboard/api/src/main/java/org/role/samples_button/feature/soundboard/api/SoundBoardNavigator.kt`

```kotlin
package org.role.samples_button.feature.soundboard.api

interface SoundBoardNavigator
```

- [ ] **Step 3: Verificar que el módulo compila**

Run: `./gradlew :feature:soundboard:api:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add feature/soundboard/api/
git commit -m "feat: add :feature:soundboard:api navigation contract"
```

---

## Task 3: Complete GroupDao

**Files:**
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`

- [ ] **Step 1: Reemplazar el contenido de `GroupDao.kt`**

El archivo actual es un esqueleto vacío. Reemplazarlo con:

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY position ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Verificar que :core:database sigue compilando**

Run: `./gradlew :core:database:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt
git commit -m "feat: complete GroupDao with getAllGroups, insert, and deleteById"
```

---

## Task 4: GroupRepositoryImpl + Hilt Modules + Tests

**Files:**
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`
- Create: `core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt`
- Create: `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`

- [ ] **Step 1: Actualizar `core/data/build.gradle.kts`**

Reemplazar el contenido completo:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.role.samples_button.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Escribir el test `FakeGroupDao.kt`**

Path: `core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt`

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity

class FakeGroupDao : GroupDao {
    private val groups = mutableListOf<GroupEntity>()
    private val flow = MutableStateFlow<List<GroupEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllGroups(): Flow<List<GroupEntity>> = flow

    override suspend fun insert(group: GroupEntity): Long {
        val entity = group.copy(id = nextId++)
        groups.add(entity)
        flow.value = groups.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        groups.removeAll { it.id == id }
        flow.value = groups.toList()
    }
}
```

- [ ] **Step 3: Escribir el test `GroupRepositoryImplTest.kt`**

Path: `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryImplTest {

    @Test
    fun `createGroup inserts group with correct name`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao())
        repo.createGroup("Drums")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups.size)
        assertEquals("Drums", groups[0].name)
    }

    @Test
    fun `deleteGroup removes group from list`() = runTest {
        val dao = FakeGroupDao()
        val repo = GroupRepositoryImpl(dao)
        repo.createGroup("Drums")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.deleteGroup(id)
        assertTrue(repo.getGroupsWithButtons().first().isEmpty())
    }

    @Test
    fun `createGroup assigns sequential positions`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao())
        repo.createGroup("First")
        repo.createGroup("Second")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(0, groups[0].position)
        assertEquals(1, groups[1].position)
    }
}
```

- [ ] **Step 4: Verificar que los tests fallan (no existe GroupRepositoryImpl aún)**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: FAILED — `GroupRepositoryImpl` not found

- [ ] **Step 5: Crear `GroupRepositoryImpl.kt`**

Path: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().map { entities ->
            entities.map { Group(it.id, it.name, it.position, emptyList()) }
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

- [ ] **Step 6: Crear `di/DatabaseModule.kt`**

Path: `core/data/src/main/java/org/role/samples_button/core/data/di/DatabaseModule.kt`

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
}
```

- [ ] **Step 7: Crear `di/RepositoryModule.kt`**

Path: `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`

```kotlin
package org.role.samples_button.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.GroupRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository
}
```

- [ ] **Step 8: Verificar que los tests pasan**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — 3 tests passing

- [ ] **Step 9: Commit**

```bash
git add core/data/
git commit -m "feat: add GroupRepositoryImpl, Hilt modules, and unit tests in :core:data"
```

---

## Task 5: SoundBoardViewModel + Tests

**Files:**
- Create: `feature/soundboard/impl/build.gradle.kts`
- Create: `feature/soundboard/impl/src/main/AndroidManifest.xml`
- Create: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- Create: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Crear `feature/soundboard/impl/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.feature.soundboard.impl"
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
    implementation(project(":feature:soundboard:api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Crear `feature/soundboard/impl/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Escribir el test `SoundBoardViewModelTest.kt`**

Path: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

```kotlin
package org.role.samples_button.feature.soundboard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

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
    fun `initial groups state is empty list`() = runTest {
        val viewModel = SoundBoardViewModel(FakeGroupRepository())
        assertEquals(emptyList<Group>(), viewModel.groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo)
        viewModel.createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo)
        viewModel.deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }
}

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    private val _groups = MutableStateFlow<List<Group>>(emptyList())

    override fun getGroupsWithButtons(): Flow<List<Group>> = _groups

    override suspend fun createGroup(name: String) {
        createdGroups.add(name)
        _groups.value = _groups.value + Group(
            id = createdGroups.size.toLong(),
            name = name,
            position = createdGroups.size - 1,
            buttons = emptyList()
        )
    }

    override suspend fun deleteGroup(id: Long) {
        deletedIds.add(id)
        _groups.value = _groups.value.filter { it.id != id }
    }

    override suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>) = Unit
}
```

- [ ] **Step 4: Verificar que el test falla (ViewModel no existe)**

Run: `./gradlew :feature:soundboard:impl:testDebugUnitTest`
Expected: FAILED — `SoundBoardViewModel` not found

- [ ] **Step 5: Crear `SoundBoardViewModel.kt`**

Path: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository
        .getGroupsWithButtons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }
}
```

- [ ] **Step 6: Verificar que los tests pasan**

Run: `./gradlew :feature:soundboard:impl:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — 3 tests passing

- [ ] **Step 7: Commit**

```bash
git add feature/soundboard/impl/
git commit -m "feat: add SoundBoardViewModel with unit tests in :feature:soundboard:impl"
```

---

## Task 6: SoundBoardScreen

**Files:**
- Create: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Crear `SoundBoardScreen.kt`**

Path: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.role.samples_button.core.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(viewModel: SoundBoardViewModel) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sin grupos todavía")
            Text("Toca + para crear uno")
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(group = group, onDelete = { onDelete(group.id) })
        }
    }
}

@Composable
private fun GroupCard(group: Group, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = group.name)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar ${group.name}")
            }
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

- [ ] **Step 2: Verificar que el módulo compila**

Run: `./gradlew :feature:soundboard:impl:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: add SoundBoardScreen with groups list, empty state, and create dialog"
```

---

## Task 7: Wire :app

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/org/role/samples_button/SamplesButtonApp.kt`
- Create: `app/src/main/java/org/role/samples_button/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Actualizar `app/build.gradle.kts`**

Reemplazar el contenido completo. Nota: se conserva el bloque `compileSdk { version = release(36) { ... } }` propio de AGP 9.x:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
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
    implementation(project(":feature:soundboard:impl"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 2: Crear `SamplesButtonApp.kt`**

Path: `app/src/main/java/org/role/samples_button/SamplesButtonApp.kt`

```kotlin
package org.role.samples_button

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SamplesButtonApp : Application()
```

- [ ] **Step 3: Crear `MainActivity.kt`**

Path: `app/src/main/java/org/role/samples_button/MainActivity.kt`

```kotlin
package org.role.samples_button

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.role.samples_button.core.designsystem.SamplesButtonTheme
import org.role.samples_button.feature.soundboard.impl.SoundBoardScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: SoundBoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                SoundBoardScreen(viewModel = viewModel)
            }
        }
    }
}
```

- [ ] **Step 4: Actualizar `app/src/main/AndroidManifest.xml`**

Reemplazar el contenido completo:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

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

- [ ] **Step 5: Compilar debug completo**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

Si hay errores de compilación, diagnosticarlos antes de continuar. Errores comunes:
- `Cannot find symbol @HiltAndroidApp` → verificar que `hilt.android` plugin está en `app/build.gradle.kts`
- `Unresolved reference: viewModels` → verificar que `activity-compose` está en las dependencias
- `Unresolved reference: SamplesButtonTheme` → verificar que `:core:designsystem` está en las dependencias

- [ ] **Step 6: Commit**

```bash
git add app/
git commit -m "feat: wire :app with Hilt, MainActivity, and SoundBoardScreen entry point"
```

---

## Task 8: Install and Verify

- [ ] **Step 1: Instalar en el emulador**

Verificar que hay un emulador conectado:
```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```
Expected: muestra `emulator-XXXX  device`

Si no hay emulador, iniciarlo desde Android Studio Device Manager antes de continuar.

Run: `./gradlew installDebug`
Expected: `BUILD SUCCESSFUL` + `Installed on 1 device.`

- [ ] **Step 2: Lanzar la app**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n "org.role.samples_button/.MainActivity"
```
Expected: `Starting: Intent { cmp=org.role.samples_button/.MainActivity }`

- [ ] **Step 3: Verificar que el proceso está corriendo**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell pidof org.role.samples_button
```
Expected: un número de PID (ej. `12345`)

Si el PID está vacío, la app crasheó. Obtener logs con:
```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d -s AndroidRuntime:E | tail -30
```

- [ ] **Step 4: Verificar estado de la pantalla vacía**

Comprobar en el emulador que se ve:
- TopAppBar con título "SoundBoard"
- Texto central "Sin grupos todavía"
- FAB con ícono `+`

- [ ] **Step 5: Commit si hubo correcciones**

Si se realizaron cambios para que la app arranque:
```bash
git add -A
git commit -m "fix: resolve runtime issues after soundboard MVP wiring"
```
