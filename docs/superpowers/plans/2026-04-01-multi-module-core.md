# Multi-Module Core Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold los 6 módulos `core/`, actualizar `libs.versions.toml` con las dependencias del stack core, y verificar que el proyecto compila completamente.

**Architecture:** Jerarquía plana bajo `core/`. `:core:model` es un módulo Kotlin JVM puro (sin Android). Los otros 5 son Android libraries. Las dependencias fluyen hacia adentro: `data` → `database` → `model`, `ui` → `designsystem` + `model`.

**Tech Stack:** Kotlin 2.1.20, AGP 9.1.0, KSP 2.1.20-1.0.32, Room 2.7.0, Compose BOM 2025.04.00, Coroutines 1.10.2

---

## File Map

**Modificados:**
- `gradle/libs.versions.toml`
- `build.gradle.kts` (raíz)
- `settings.gradle.kts`

**Creados:**
- `core/model/build.gradle.kts`
- `core/model/src/main/java/org/role/samples_button/core/model/Group.kt`
- `core/model/src/main/java/org/role/samples_button/core/model/SoundButton.kt`
- `core/database/build.gradle.kts`
- `core/database/src/main/AndroidManifest.xml`
- `core/database/src/main/java/org/role/samples_button/core/database/GroupEntity.kt`
- `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonEntity.kt`
- `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`
- `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`
- `core/database/src/main/java/org/role/samples_button/core/database/AppDatabase.kt`
- `core/audio/build.gradle.kts`
- `core/audio/src/main/AndroidManifest.xml`
- `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`
- `core/data/build.gradle.kts`
- `core/data/src/main/AndroidManifest.xml`
- `core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt`
- `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`
- `core/designsystem/build.gradle.kts`
- `core/designsystem/src/main/AndroidManifest.xml`
- `core/designsystem/src/main/java/org/role/samples_button/core/designsystem/Theme.kt`
- `core/ui/build.gradle.kts`
- `core/ui/src/main/AndroidManifest.xml`
- `core/ui/src/main/java/org/role/samples_button/core/ui/SoundButtonCard.kt`

---

## Task 1: Update Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Reemplazar el contenido completo de `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.1.0"
coreKtx = "1.18.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
appcompat = "1.6.1"
material = "1.10.0"
kotlin = "2.1.20"
ksp = "2.1.20-1.0.32"
hilt = "2.56.1"
room = "2.7.0"
coroutines = "1.10.2"
composeBom = "2025.04.00"
lifecycle = "2.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Verificar que el catálogo parsea**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add core stack to Version Catalog"
```

---

## Task 2: Update Root Gradle Files

**Files:**
- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Reemplazar el contenido completo de `build.gradle.kts` (raíz)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 2: Agregar includes al final de `settings.gradle.kts`**

Añadir después de `include(":app")`:

```kotlin
include(":core:model")
include(":core:database")
include(":core:audio")
include(":core:data")
include(":core:ui")
include(":core:designsystem")
```

- [ ] **Step 3: Verificar que los módulos están registrados**

Run: `./gradlew projects`
Expected output incluye `:core:model`, `:core:database`, `:core:audio`, `:core:data`, `:core:ui`, `:core:designsystem`

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts settings.gradle.kts
git commit -m "build: register core modules in settings.gradle.kts"
```

---

## Task 3: Create :core:model

**Files:**
- Create: `core/model/build.gradle.kts`
- Create: `core/model/src/main/java/org/role/samples_button/core/model/Group.kt`
- Create: `core/model/src/main/java/org/role/samples_button/core/model/SoundButton.kt`

- [ ] **Step 1: Crear `core/model/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}
```

- [ ] **Step 2: Crear `Group.kt`**

Path: `core/model/src/main/java/org/role/samples_button/core/model/Group.kt`

```kotlin
package org.role.samples_button.core.model

data class Group(
    val id: Long,
    val name: String,
    val position: Int,
    val buttons: List<SoundButton>
)
```

- [ ] **Step 3: Crear `SoundButton.kt`**

Path: `core/model/src/main/java/org/role/samples_button/core/model/SoundButton.kt`

```kotlin
package org.role.samples_button.core.model

data class SoundButton(
    val id: Long,
    val label: String,
    val filePath: String,
    val groupId: Long,
    val position: Int
)
```

- [ ] **Step 4: Verificar que el módulo compila**

Run: `./gradlew :core:model:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/model/
git commit -m "feat: add :core:model with Group and SoundButton domain models"
```

---

## Task 4: Create :core:database

**Files:**
- Create: `core/database/build.gradle.kts`
- Create: `core/database/src/main/AndroidManifest.xml`
- Create: `core/database/src/main/java/org/role/samples_button/core/database/GroupEntity.kt`
- Create: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonEntity.kt`
- Create: `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`
- Create: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`
- Create: `core/database/src/main/java/org/role/samples_button/core/database/AppDatabase.kt`

- [ ] **Step 1: Crear `core/database/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.role.samples_button.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
```

- [ ] **Step 2: Crear `core/database/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Crear `GroupEntity.kt`**

Path: `core/database/src/main/java/org/role/samples_button/core/database/GroupEntity.kt`

```kotlin
package org.role.samples_button.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int
)
```

- [ ] **Step 4: Crear `SoundButtonEntity.kt`**

Path: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonEntity.kt`

```kotlin
package org.role.samples_button.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_buttons")
data class SoundButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val filePath: String,
    val groupId: Long,
    val position: Int
)
```

- [ ] **Step 5: Crear `GroupDao.kt`**

Path: `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao

@Dao
interface GroupDao
```

- [ ] **Step 6: Crear `SoundButtonDao.kt`**

Path: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao

@Dao
interface SoundButtonDao
```

- [ ] **Step 7: Crear `AppDatabase.kt`**

Path: `core/database/src/main/java/org/role/samples_button/core/database/AppDatabase.kt`

```kotlin
package org.role.samples_button.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, SoundButtonEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun soundButtonDao(): SoundButtonDao
}
```

- [ ] **Step 8: Verificar que el módulo compila**

Run: `./gradlew :core:database:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add core/database/
git commit -m "feat: add :core:database with Room entities, DAOs, and AppDatabase"
```

---

## Task 5: Create :core:audio

**Files:**
- Create: `core/audio/build.gradle.kts`
- Create: `core/audio/src/main/AndroidManifest.xml`
- Create: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`

- [ ] **Step 1: Crear `core/audio/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.role.samples_button.core.audio"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
```

- [ ] **Step 2: Crear `core/audio/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Crear `SoundPoolManager.kt`**

Path: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`

```kotlin
package org.role.samples_button.core.audio

class SoundPoolManager {
    fun play(filePath: String): Unit = TODO("Implement SoundPool playback")
    fun release(): Unit = TODO("Implement SoundPool release")
}
```

- [ ] **Step 4: Verificar que el módulo compila**

Run: `./gradlew :core:audio:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/audio/
git commit -m "feat: add :core:audio module with SoundPoolManager skeleton"
```

---

## Task 6: Create :core:data

**Files:**
- Create: `core/data/build.gradle.kts`
- Create: `core/data/src/main/AndroidManifest.xml`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt`
- Create: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`

- [ ] **Step 1: Crear `core/data/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
}
```

- [ ] **Step 2: Crear `core/data/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Crear `GroupRepository.kt`**

Path: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt`

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

interface GroupRepository {
    fun getGroupsWithButtons(): Flow<List<Group>>
    suspend fun createGroup(name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>)
}
```

- [ ] **Step 4: Crear `SoundButtonRepository.kt`**

Path: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`

```kotlin
package org.role.samples_button.core.data

import org.role.samples_button.core.model.SoundButton

interface SoundButtonRepository {
    suspend fun addButton(button: SoundButton)
    suspend fun deleteButton(id: Long)
}
```

- [ ] **Step 5: Verificar que el módulo compila**

Run: `./gradlew :core:data:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/data/
git commit -m "feat: add :core:data with GroupRepository and SoundButtonRepository interfaces"
```

---

## Task 7: Create :core:designsystem

**Files:**
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/main/AndroidManifest.xml`
- Create: `core/designsystem/src/main/java/org/role/samples_button/core/designsystem/Theme.kt`

(Se crea antes de `:core:ui` porque `:core:ui` depende de este módulo)

- [ ] **Step 1: Crear `core/designsystem/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.core.designsystem"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 2: Crear `core/designsystem/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Crear `Theme.kt`**

Path: `core/designsystem/src/main/java/org/role/samples_button/core/designsystem/Theme.kt`

```kotlin
package org.role.samples_button.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SamplesButtonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(content = content)
}
```

- [ ] **Step 4: Verificar que el módulo compila**

Run: `./gradlew :core:designsystem:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/
git commit -m "feat: add :core:designsystem with SamplesButtonTheme"
```

---

## Task 8: Create :core:ui

**Files:**
- Create: `core/ui/build.gradle.kts`
- Create: `core/ui/src/main/AndroidManifest.xml`
- Create: `core/ui/src/main/java/org/role/samples_button/core/ui/SoundButtonCard.kt`

- [ ] **Step 1: Crear `core/ui/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 2: Crear `core/ui/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 3: Crear `SoundButtonCard.kt`**

Path: `core/ui/src/main/java/org/role/samples_button/core/ui/SoundButtonCard.kt`

```kotlin
package org.role.samples_button.core.ui

import androidx.compose.runtime.Composable
import org.role.samples_button.core.model.SoundButton

@Composable
fun SoundButtonCard(
    button: SoundButton,
    onClick: () -> Unit
) {
    TODO("Implement in soundboard feature")
}
```

- [ ] **Step 4: Verificar que el módulo compila**

Run: `./gradlew :core:ui:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/ui/
git commit -m "feat: add :core:ui with SoundButtonCard composable skeleton"
```

---

## Task 9: Full Build Verification

- [ ] **Step 1: Build debug completo**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` — todos los módulos (`:app` + 6 `:core:*`) compilan sin errores

- [ ] **Step 2: Verificar grafo de dependencias de :core:data**

Run: `./gradlew :core:data:dependencies --configuration releaseRuntimeClasspath`
Expected: el output muestra `:core:model` y `:core:database` en el árbol de dependencias

- [ ] **Step 3: Commit si hubo correcciones**

Si alguno de los pasos anteriores requirió cambios para compilar:

```bash
git add -A
git commit -m "build: fix compilation issues after core module scaffold"
```
