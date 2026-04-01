# Feature Soundboard MVP — Design Spec

**Date:** 2026-04-01  
**Scope:** Implementar `feature/soundboard` en su versión mínima viable: pantalla principal con lista de grupos, crear/eliminar grupos, persistencia en Room, Hilt DI completo. Sin reproducción de audio, sin drag & drop, sin FileBrowser (las features siguientes lo añadirán).

---

## 1. Módulos nuevos y cambios

### Nuevos módulos

```
feature/
└── soundboard/
    ├── api/    — Kotlin JVM puro: contrato de navegación (vacío en MVP)
    └── impl/   — Android library: SoundBoardScreen + SoundBoardViewModel
```

### Cambios en módulos existentes

| Módulo | Cambio |
|---|---|
| `:core:database` | Completar `GroupDao` con queries reales |
| `:core:data` | Añadir `GroupRepositoryImpl` + `DatabaseModule` (Hilt) |
| `:app` | Añadir `SamplesButtonApp`, `MainActivity`, actualizar manifest y `build.gradle.kts` |
| `settings.gradle.kts` | Registrar `:feature:soundboard:api` y `:feature:soundboard:impl` |
| `build.gradle.kts` (raíz) | No cambios necesarios (plugins ya declarados) |

### Grafo de dependencias

```
:feature:soundboard:impl
    → :feature:soundboard:api
    → :core:data
    → :core:model
    → :core:ui
    → :core:designsystem

:app → :feature:soundboard:impl
     → :core:designsystem
```

---

## 2. Capa de datos

### `core/database/src/.../GroupDao.kt` (reemplazar esqueleto)

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Delete
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

### `core/data/src/.../GroupRepositoryImpl.kt` (nuevo)

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

### `core/data/src/.../di/DatabaseModule.kt` (nuevo)

Módulo Hilt instalado en `SingletonComponent`:
- `@Provides @Singleton` → `AppDatabase` via `Room.databaseBuilder(context, AppDatabase::class.java, "soundboard.db").build()`
- `@Provides` → `GroupDao` desde `AppDatabase.groupDao()`
- `@Binds @Singleton` → `GroupRepository` bound a `GroupRepositoryImpl`

Requiere `@Module @InstallIn(SingletonComponent::class)` + `abstract` para Binds.
Se puede separar en dos clases: `DatabaseModule` (Provides concretas) y `RepositoryModule` (Binds abstracto).

---

## 3. Feature: soundboard

### `feature/soundboard/api/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(11) }
```

Sin dependencias — el API solo define contratos de navegación.

### `feature/soundboard/api/src/.../soundboard/api/SoundBoardNavigator.kt`

```kotlin
package org.role.samples_button.feature.soundboard.api

interface SoundBoardNavigator
```

Vacío en MVP. Se completará cuando se añada navegación entre features.

### `feature/soundboard/impl/build.gradle.kts`

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
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":feature:soundboard:api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
```

### `feature/soundboard/impl/src/.../SoundBoardViewModel.kt`

```kotlin
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

### `feature/soundboard/impl/src/.../SoundBoardScreen.kt`

Composable `SoundBoardScreen(viewModel: SoundBoardViewModel = hiltViewModel())`:

- Observa `viewModel.groups` con `collectAsStateWithLifecycle()`
- Estado local `showCreateDialog: Boolean`
- **Si la lista está vacía:** columna centrada con texto "Sin grupos todavía" + "Toca + para crear uno"
- **Si hay grupos:** `LazyColumn` con una `Card` por grupo mostrando nombre + icono de papelera (llama `viewModel.deleteGroup(group.id)`)
- `FloatingActionButton` con ícono `+` que activa `showCreateDialog = true`
- `AlertDialog` visible cuando `showCreateDialog`:
  - `TextField` para el nombre
  - Botón "Crear" que llama `viewModel.createGroup(name)` y cierra el diálogo
  - Botón "Cancelar"

---

## 4. Entry point en `:app`

### `app/src/main/java/.../SamplesButtonApp.kt`

```kotlin
@HiltAndroidApp
class SamplesButtonApp : Application()
```

### `app/src/main/java/.../MainActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                SoundBoardScreen()
            }
        }
    }
}
```

### `app/src/main/AndroidManifest.xml`

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

### `app/build.gradle.kts` — plugins añadidos

```kotlin
alias(libs.plugins.hilt.android)
alias(libs.plugins.ksp)
alias(libs.plugins.compose.compiler)
```

### `app/build.gradle.kts` — dependencias añadidas

```kotlin
implementation(project(":feature:soundboard:impl"))
implementation(project(":core:designsystem"))
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.material3)
implementation(libs.lifecycle.viewmodel.compose)
buildFeatures { compose = true }
```

---

## 5. `settings.gradle.kts` — includes añadidos

```kotlin
include(":feature:soundboard:api")
include(":feature:soundboard:impl")
```

---

## 6. Criterios de éxito

- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- La app instala y abre en el emulador mostrando `SoundBoardScreen`
- Pantalla vacía muestra "Sin grupos todavía"
- El FAB `+` abre un diálogo para crear un grupo
- Al crear un grupo, aparece en la lista
- Al pulsar el ícono de papelera, el grupo se elimina
- Los grupos persisten entre reinicios de la app (Room)
