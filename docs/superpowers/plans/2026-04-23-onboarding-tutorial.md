# Onboarding Tutorial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mostrar un pager de bienvenida de 5 páginas la primera vez que el usuario abre la app, con opción de omitir, y relanzable desde el menú principal.

**Architecture:** Se añade DataStore a `core/data` para persistir el flag `has_seen_onboarding`. `SoundBoardViewModel` expone `hasSeenOnboarding: StateFlow<Boolean?>` y redirige a la ruta `"onboarding"` en la primera ejecución. `feature/onboarding/impl` contiene `OnboardingScreen` (stateless) y `OnboardingViewModel` (llama a `markSeen()`).

**Tech Stack:** Jetpack Compose HorizontalPager, DataStore Preferences 1.1.1, Hilt, Compose Navigation.

---

## Mapa de archivos

| Acción | Archivo |
|--------|---------|
| Modificar | `gradle/libs.versions.toml` |
| Modificar | `core/data/build.gradle` |
| Crear | `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepository.kt` |
| Crear | `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepositoryImpl.kt` |
| Crear | `core/data/src/main/java/org/role/samples_button/core/data/di/PreferencesModule.kt` |
| Modificar | `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt` |
| Modificar | `feature/soundboard/impl/src/test/.../SoundBoardViewModelTest.kt` |
| Modificar | `feature/soundboard/impl/src/main/.../SoundBoardViewModel.kt` |
| Modificar | `settings.gradle` |
| Crear | `feature/onboarding/impl/build.gradle` |
| Crear | `feature/onboarding/impl/src/main/AndroidManifest.xml` |
| Crear | `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingViewModel.kt` |
| Crear | `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingScreen.kt` |
| Modificar | `app/build.gradle` |
| Modificar | `app/src/main/java/org/role/samples_button/MainActivity.kt` |
| Modificar | `feature/soundboard/impl/src/main/.../SoundBoardScreen.kt` |

---

## Task 1: Añadir dependencia DataStore

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle`

- [ ] **Step 1: Añadir versión y alias en libs.versions.toml**

En `gradle/libs.versions.toml`, dentro de `[versions]` añadir:
```toml
datastore = "1.1.1"
```

Dentro de `[libraries]` añadir:
```toml
androidx-datastore = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Añadir dependencia en core/data/build.gradle**

En `core/data/build.gradle`, dentro del bloque `dependencies {}` añadir:
```groovy
implementation(libs.androidx.datastore)
```

- [ ] **Step 3: Verificar que el proyecto sincroniza**

```bash
./gradlew :core:data:dependencies --configuration releaseRuntimeClasspath | grep datastore
```

Esperado: línea que muestra `androidx.datastore:datastore-preferences:1.1.1`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml core/data/build.gradle
git commit -m "build: add DataStore Preferences dependency to core:data"
```

---

## Task 2: UserPreferencesRepository — interfaz e implementación

**Files:**
- Create: `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepository.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepositoryImpl.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/di/PreferencesModule.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`

- [ ] **Step 1: Crear la interfaz**

Crear `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepository.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val hasSeenOnboarding: Flow<Boolean>
    suspend fun markSeen()
}
```

- [ ] **Step 2: Crear la implementación con DataStore**

Crear `core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private val hasSeenKey = booleanPreferencesKey("has_seen_onboarding")

    override val hasSeenOnboarding: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[hasSeenKey] ?: false }

    override suspend fun markSeen() {
        dataStore.edit { prefs -> prefs[hasSeenKey] = true }
    }
}
```

- [ ] **Step 3: Crear PreferencesModule (provee DataStore)**

Crear `core/data/src/main/java/org/role/samples_button/core/data/di/PreferencesModule.kt`:

```kotlin
package org.role.samples_button.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
```

- [ ] **Step 4: Registrar el binding en RepositoryModule**

Reemplazar el contenido de `core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt`:

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
import org.role.samples_button.core.data.UserPreferencesRepository
import org.role.samples_button.core.data.UserPreferencesRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds @Singleton
    abstract fun bindSoundButtonRepository(impl: SoundButtonRepositoryImpl): SoundButtonRepository

    @Binds @Singleton
    abstract fun bindAudioFileRepository(impl: AudioFileRepositoryImpl): AudioFileRepository

    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
```

- [ ] **Step 5: Verificar compilación de core:data**

```bash
./gradlew :core:data:compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepository.kt \
        core/data/src/main/java/org/role/samples_button/core/data/UserPreferencesRepositoryImpl.kt \
        core/data/src/main/java/org/role/samples_button/core/data/di/PreferencesModule.kt \
        core/data/src/main/java/org/role/samples_button/core/data/di/RepositoryModule.kt
git commit -m "feat: add UserPreferencesRepository with DataStore for onboarding flag"
```

---

## Task 3: Actualizar SoundBoardViewModel (TDD)

**Files:**
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`

- [ ] **Step 1: Escribir las pruebas que fallarán**

Reemplazar el contenido de `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.data.UserPreferencesRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        groups: FakeGroupRepository = FakeGroupRepository(),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()
    ) = SoundBoardViewModel(groups, prefs)

    @Test
    fun `initial groups state is empty list`() = runTest {
        assertEquals(emptyList<Group>(), vm().groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }

    @Test
    fun `renameGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).renameGroup(5L, "Renamed")
        assertEquals(listOf(5L to "Renamed"), repo.renamedGroups)
    }

    @Test
    fun `reorderGroups delegates reordered list with updated positions to repository`() = runTest {
        val repo = FakeGroupRepository()
        val groupA = Group(id = 1L, name = "A", position = 0, buttons = emptyList())
        val groupB = Group(id = 2L, name = "B", position = 1, buttons = emptyList())
        val groupC = Group(id = 3L, name = "C", position = 2, buttons = emptyList())
        repo.seedGroups(listOf(groupA, groupB, groupC))

        val viewModel = vm(groups = repo)
        val job = launch { viewModel.groups.collect { } }
        advanceUntilIdle()
        job.cancel()

        viewModel.reorderGroups(from = 0, to = 2)
        advanceUntilIdle()

        val reordered = repo.reorderedLists.last()
        assertEquals(3, reordered.size)
        assertEquals(2L, reordered[0].id)
        assertEquals(0, reordered[0].position)
        assertEquals(3L, reordered[1].id)
        assertEquals(1, reordered[1].position)
        assertEquals(1L, reordered[2].id)
        assertEquals(2, reordered[2].position)
    }

    @Test
    fun `hasSeenOnboarding initial value is null`() = runTest {
        assertNull(vm().hasSeenOnboarding.value)
    }

    @Test
    fun `hasSeenOnboarding emits false when prefs returns false`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = false)
        val viewModel = vm(prefs = prefs)
        val job = launch { viewModel.hasSeenOnboarding.collect { } }
        advanceUntilIdle()
        assertEquals(false, viewModel.hasSeenOnboarding.value)
        job.cancel()
    }

    @Test
    fun `hasSeenOnboarding emits true when prefs returns true`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = true)
        val viewModel = vm(prefs = prefs)
        val job = launch { viewModel.hasSeenOnboarding.collect { } }
        advanceUntilIdle()
        assertEquals(true, viewModel.hasSeenOnboarding.value)
        job.cancel()
    }

    @Test
    fun `markOnboardingSeen delegates to repository`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = false)
        val viewModel = vm(prefs = prefs)
        viewModel.markOnboardingSeen()
        advanceUntilIdle()
        assertTrue(prefs.markSeenCalled)
    }
}

// ── Fakes ──────────────────────────────────────────────────────────────────

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    val renamedGroups = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<Group>>()
    val _groups = MutableStateFlow<List<Group>>(emptyList())

    override fun getGroupsWithButtons(): Flow<List<Group>> = _groups
    override suspend fun createGroup(name: String) {
        createdGroups.add(name)
        _groups.value = _groups.value + Group(createdGroups.size.toLong(), name, createdGroups.size - 1, emptyList())
    }
    override suspend fun deleteGroup(id: Long) { deletedIds.add(id); _groups.value = _groups.value.filter { it.id != id } }
    override suspend fun renameGroup(id: Long, newName: String) { renamedGroups.add(id to newName); _groups.value = _groups.value.map { if (it.id == id) it.copy(name = newName) else it } }
    override suspend fun reorderGroups(groups: List<Group>) { reorderedLists.add(groups); _groups.value = groups }
    fun seedGroups(groups: List<Group>) { _groups.value = groups }
}

class FakeUserPreferencesRepository(initial: Boolean = false) : UserPreferencesRepository {
    var markSeenCalled = false
    private val _flow = MutableStateFlow(initial)
    override val hasSeenOnboarding: Flow<Boolean> get() = _flow
    override suspend fun markSeen() { markSeenCalled = true; _flow.value = true }
}

class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    val renamedButtons = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<SoundButton>>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
    override suspend fun renameButton(id: Long, newLabel: String) { renamedButtons.add(id to newLabel) }
    override suspend fun reorderButtons(buttons: List<SoundButton>) { reorderedLists.add(buttons) }
}
```

- [ ] **Step 2: Ejecutar tests para confirmar que fallan**

```bash
./gradlew :feature:soundboard:impl:test --tests "*.SoundBoardViewModelTest" 2>&1 | tail -20
```

Esperado: error de compilación — `SoundBoardViewModel` no acepta `UserPreferencesRepository` todavía.

- [ ] **Step 3: Actualizar SoundBoardViewModel**

Reemplazar el contenido de `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`:

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
import org.role.samples_button.core.data.UserPreferencesRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository
        .getGroupsWithButtons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasSeenOnboarding: StateFlow<Boolean?> = userPreferencesRepository
        .hasSeenOnboarding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markOnboardingSeen() {
        viewModelScope.launch { userPreferencesRepository.markSeen() }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }

    fun renameGroup(id: Long, newName: String) {
        viewModelScope.launch { groupRepository.renameGroup(id, newName) }
    }

    fun reorderGroups(from: Int, to: Int) {
        val current = groups.value
        val reordered = current.toMutableList()
            .apply { add(to, removeAt(from)) }
            .mapIndexed { index, grp -> grp.copy(position = index) }
        viewModelScope.launch { groupRepository.reorderGroups(reordered) }
    }
}
```

- [ ] **Step 4: Ejecutar tests y confirmar que pasan**

```bash
./gradlew :feature:soundboard:impl:test --tests "*.SoundBoardViewModelTest" 2>&1 | tail -20
```

Esperado: `BUILD SUCCESSFUL` — todos los tests en verde.

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: expose hasSeenOnboarding and markOnboardingSeen in SoundBoardViewModel"
```

---

## Task 4: Crear módulo feature:onboarding:impl

**Files:**
- Modify: `settings.gradle`
- Create: `feature/onboarding/impl/build.gradle`
- Create: `feature/onboarding/impl/src/main/AndroidManifest.xml`
- Create: `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingViewModel.kt`
- Create: `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingScreen.kt`

- [ ] **Step 1: Registrar el módulo en settings.gradle**

Añadir al final de `settings.gradle`:
```groovy
include(":feature:onboarding:impl")
```

- [ ] **Step 2: Crear build.gradle del módulo**

Crear `feature/onboarding/impl/build.gradle`:

```groovy
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.feature.onboarding.impl"
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
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 3: Crear el AndroidManifest.xml**

Crear `feature/onboarding/impl/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Crear OnboardingViewModel**

Crear `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingViewModel.kt`:

```kotlin
package org.role.samples_button.feature.onboarding.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    fun markSeen() {
        viewModelScope.launch { userPreferencesRepository.markSeen() }
    }
}
```

- [ ] **Step 5: Crear OnboardingScreen**

Crear `feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingScreen.kt`:

```kotlin
package org.role.samples_button.feature.onboarding.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        emoji = "🎛️",
        title = "Bienvenido a SoundBoard",
        body = "Tu sampler portátil. Organiza audios en grupos y dispáralos al instante."
    ),
    OnboardingPage(
        emoji = "📁",
        title = "Crea un grupo",
        body = "Toca ＋ para crear tu primer grupo.\nEjemplo: \"Percusión\", \"Efectos\", \"Melodías\"."
    ),
    OnboardingPage(
        emoji = "🎵",
        title = "Añade samples",
        body = "Dentro de un grupo toca \"Añadir sample\" para explorar tus archivos de audio."
    ),
    OnboardingPage(
        emoji = "▶️",
        title = "Reproduce",
        body = "Toca ▶ para reproducir. Usa la barra de progreso para saltar a cualquier punto del audio."
    ),
    OnboardingPage(
        emoji = "⚙️",
        title = "Controles extra",
        body = "🔁 Bucle — repite el sample en bucle\n↩️ Restart — vuelve al inicio\n↕️ Mantén pulsado para reordenar"
    )
)

@Composable
fun OnboardingScreen(
    isFirstRun: Boolean,
    onFinish: () -> Unit,
    onMarkSeen: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    fun finish() {
        if (isFirstRun) onMarkSeen()
        onFinish()
    }

    BackHandler(enabled = isFirstRun) { finish() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            // Barra de navegación inferior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { finish() }) {
                    Text("Omitir")
                }

                // Indicadores de página
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Surface(
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .height(4.dp)
                                .then(
                                    if (isSelected)
                                        Modifier.padding(horizontal = 0.dp).height(4.dp)
                                    else Modifier
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .padding(horizontal = if (isSelected) 8.dp else 4.dp)
                            )
                        }
                    }
                }

                val isLastPage = pagerState.currentPage == pages.size - 1
                Button(
                    onClick = {
                        if (isLastPage) {
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Text(
                        if (isLastPage) {
                            if (isFirstRun) "¡Empezar!" else "Cerrar"
                        } else {
                            "Siguiente →"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = page.emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 6: Verificar compilación del módulo**

```bash
./gradlew :feature:onboarding:impl:compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add settings.gradle \
        feature/onboarding/impl/build.gradle \
        feature/onboarding/impl/src/main/AndroidManifest.xml \
        feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingViewModel.kt \
        feature/onboarding/impl/src/main/java/org/role/samples_button/feature/onboarding/impl/OnboardingScreen.kt
git commit -m "feat: add feature:onboarding:impl module with OnboardingScreen and OnboardingViewModel"
```

---

## Task 5: Conectar onboarding en la app

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/org/role/samples_button/MainActivity.kt`

- [ ] **Step 1: Añadir dependencia en app/build.gradle**

En `app/build.gradle`, dentro del bloque `dependencies {}`, añadir:
```groovy
implementation(project(":feature:onboarding:impl"))
```

- [ ] **Step 2: Actualizar MainActivity con la ruta onboarding**

Reemplazar el contenido de `app/src/main/java/org/role/samples_button/MainActivity.kt`:

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
import org.role.samples_button.feature.onboarding.impl.OnboardingScreen
import org.role.samples_button.feature.onboarding.impl.OnboardingViewModel
import org.role.samples_button.feature.soundboard.impl.GroupDetailScreen
import org.role.samples_button.feature.soundboard.impl.GroupDetailViewModel
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
                            onNavigateToGroup = { groupId ->
                                navController.navigate("group_detail/$groupId")
                            },
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            },
                            onNavigateToOnboarding = {
                                navController.navigate("onboarding?firstRun=true")
                            }
                        )
                    }

                    composable(
                        route = "group_detail/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) {
                        val viewModel: GroupDetailViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        GroupDetailScreen(
                            viewModel = viewModel,
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            },
                            onNavigateBack = { navController.popBackStack() }
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

                    composable(
                        route = "onboarding?firstRun={firstRun}",
                        arguments = listOf(navArgument("firstRun") {
                            type = NavType.BoolType
                            defaultValue = false
                        })
                    ) { backStackEntry ->
                        val isFirstRun = backStackEntry.arguments?.getBoolean("firstRun") ?: false
                        val viewModel: OnboardingViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        OnboardingScreen(
                            isFirstRun = isFirstRun,
                            onFinish = { navController.popBackStack() },
                            onMarkSeen = { viewModel.markSeen() }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verificar compilación del módulo app**

```bash
./gradlew :app:compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle app/src/main/java/org/role/samples_button/MainActivity.kt
git commit -m "feat: wire onboarding route in NavHost and app dependencies"
```

---

## Task 6: Actualizar SoundBoardScreen

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Añadir LaunchedEffect y menú "Ver tutorial" en SoundBoardScreen**

El método `SoundBoardScreen` necesita tres cambios:
1. Nuevo parámetro `onNavigateToOnboarding`
2. `LaunchedEffect` que navega si `hasSeenOnboarding == false`
3. Menú ⋮ en el `TopAppBar` con el item "Ver tutorial"

Reemplazar el contenido de `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.Group
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(
    viewModel: SoundBoardViewModel,
    onNavigateToGroup: (Long) -> Unit = {},
    onNavigateToFileBrowser: (Long) -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {}
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    LaunchedEffect(hasSeenOnboarding) {
        if (hasSeenOnboarding == false) {
            onNavigateToOnboarding()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SoundBoard") },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ver tutorial") },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToOnboarding()
                                }
                            )
                        }
                    }
                }
            )
        },
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
                onNavigateToGroup = onNavigateToGroup,
                onAddSound = onNavigateToFileBrowser,
                onRenameGroup = { id, newName -> viewModel.renameGroup(id, newName) },
                onDeleteGroup = { viewModel.deleteGroup(it) },
                onReorder = { from, to -> viewModel.reorderGroups(from, to) },
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
    onNavigateToGroup: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onRenameGroup: (Long, String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            ReorderableItem(reorderState, key = group.id) { isDragging ->
                GroupCard(
                    group = group,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onNavigateToGroup = { onNavigateToGroup(group.id) },
                    onAddSound = { onAddSound(group.id) },
                    onRename = { newName -> onRenameGroup(group.id, newName) },
                    onDelete = { onDeleteGroup(group.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    isDragging: Boolean,
    dragModifier: Modifier,
    onNavigateToGroup: () -> Unit,
    onAddSound: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToGroup() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = group.buttons.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onAddSound) {
                Icon(Icons.Default.Add, contentDescription = "Añadir sample a ${group.name}")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${group.name}")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; showRenameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = group.name,
            onConfirm = { newName -> showRenameDialog = false; onRename(newName) },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar grupo") },
            text = { Text("¿Eliminar \"${group.name}\" y todos sus samples?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
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
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Ejecutar todos los tests de soundboard**

```bash
./gradlew :feature:soundboard:impl:test 2>&1 | tail -20
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: add first-run redirect and Ver tutorial menu to SoundBoardScreen"
```

---

## Task 7: Build final y verificación

- [ ] **Step 1: Ejecutar todos los tests del proyecto**

```bash
./gradlew test 2>&1 | tail -30
```

Esperado: `BUILD SUCCESSFUL` — todos los módulos en verde.

- [ ] **Step 2: Build de release**

```bash
./gradlew assembleRelease 2>&1 | tail -10
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit final si hay cambios pendientes**

```bash
git status
# Si hay algo sin commitear:
git add -A
git commit -m "chore: onboarding tutorial complete"
```
