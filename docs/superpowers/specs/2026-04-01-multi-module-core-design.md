# Multi-Module Core Structure + Version Catalog — Design Spec

**Date:** 2026-04-01  
**Scope:** Crear la estructura de módulos `core/` y completar `libs.versions.toml` con las dependencias necesarias para esos módulos. Los módulos `feature/` son fuera del alcance de este spec.

---

## 1. Estructura de módulos

```
samplesbutton/
├── app/                          # existente, sin cambios en código
├── core/
│   ├── model/                    # Kotlin JVM — data classes de dominio
│   ├── database/                 # Android library — Room
│   ├── audio/                    # Android library — SoundPoolManager
│   ├── data/                     # Android library — repositorios
│   ├── ui/                       # Android library — composables reutilizables
│   └── designsystem/             # Android library — tema, colores, tipografía
├── build.gradle.kts              # raíz — añadir plugins con apply false
├── settings.gradle.kts           # añadir include de los 6 módulos
└── gradle/libs.versions.toml     # completar versiones y dependencias
```

### Tipo de módulo por módulo

| Módulo | Plugin | Razón |
|---|---|---|
| `:core:model` | `kotlin("jvm")` | Solo data classes, sin dependencias Android |
| `:core:database` | `com.android.library` | Room requiere Context de Android |
| `:core:audio` | `com.android.library` | SoundPool es API de Android |
| `:core:data` | `com.android.library` | Depende de Room y Context |
| `:core:ui` | `com.android.library` | Jetpack Compose requiere Android |
| `:core:designsystem` | `com.android.library` | Jetpack Compose requiere Android |

### Grafo de dependencias entre módulos

```
:core:model
    ↑
:core:database
    ↑
:core:data ← :core:model
    ↑
:app ← :core:audio
     ← :core:ui ← :core:model
                 ← :core:designsystem
```

---

## 2. Version Catalog (`gradle/libs.versions.toml`)

### Versiones añadidas

| Alias | Librería | Versión |
|---|---|---|
| `kotlin` | Kotlin | 2.1.20 |
| `ksp` | KSP | 2.1.20-1.0.32 |
| `hilt` | Hilt | 2.56.1 |
| `room` | Room | 2.7.0 |
| `coroutines` | Kotlinx Coroutines | 1.10.2 |
| `composeBom` | Compose BOM | 2025.04.00 |
| `lifecycle` | Lifecycle / ViewModel | 2.9.0 |

### Librerías añadidas

- `kotlinx-coroutines-core`
- `kotlinx-coroutines-android`
- `hilt-android` + `hilt-compiler`
- `room-runtime` + `room-ktx` + `room-compiler`
- `compose-bom` (platform BOM)
- `compose-ui` + `compose-ui-tooling-preview` + `compose-material3`
- `compose-ui-tooling` (debugImplementation)
- `lifecycle-viewmodel-ktx` + `lifecycle-viewmodel-compose` + `lifecycle-runtime-ktx`

### Plugins añadidos

| Alias | ID |
|---|---|
| `android-library` | `com.android.library` |
| `kotlin-android` | `org.jetbrains.kotlin.android` |
| `kotlin-jvm` | `org.jetbrains.kotlin.jvm` |
| `hilt-android` | `com.google.dagger.hilt.android` |
| `ksp` | `com.google.devtools.ksp` |
| `compose-compiler` | `org.jetbrains.kotlin.plugin.compose` |

Las entradas existentes (`agp`, `coreKtx`, `junit`, `appcompat`, `material`, etc.) se conservan sin cambios.

---

## 3. Configuración de cada `build.gradle.kts`

### `core/model` — Kotlin JVM

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

Sin bloque `android {}`. Sin dependencias externas (solo stdlib de Kotlin incluida implícitamente).

### `core/database` — Android library + Room + KSP

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.role.samples_button.core.database"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
```

### `core/audio` — Android library

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.role.samples_button.core.audio"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
```

### `core/data` — Android library + Hilt + KSP

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.role.samples_button.core.data"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
```

### `core/ui` — Android library + Compose

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.core.ui"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
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

### `core/designsystem` — Android library + Compose

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.role.samples_button.core.designsystem"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
```

---

## 4. Cambios en archivos existentes

### `settings.gradle.kts`

Añadir al final:

```kotlin
include(":core:model")
include(":core:database")
include(":core:audio")
include(":core:data")
include(":core:ui")
include(":core:designsystem")
```

### `build.gradle.kts` (raíz)

Añadir los nuevos plugins con `apply false`:

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

---

## 5. Archivos fuente iniciales (esqueletos)

Cada módulo incluirá un único archivo Kotlin de esqueleto para que el módulo compile:

- `core/model` → `Group.kt`, `SoundButton.kt` (data classes del CLAUDE.md)
- `core/database` → `AppDatabase.kt` (clase `@Database` vacía), `GroupDao.kt`, `SoundButtonDao.kt`
- `core/audio` → `SoundPoolManager.kt` (clase vacía con `TODO()`)
- `core/data` → `GroupRepository.kt` (interface), `SoundButtonRepository.kt` (interface)
- `core/ui` → `SoundButtonCard.kt` (composable vacío con `TODO()`)
- `core/designsystem` → `Theme.kt` (MaterialTheme básico)

---

## 6. Criterios de éxito

- `./gradlew assembleDebug` compila sin errores
- Todos los módulos `:core:*` aparecen en el grafo de Gradle
- No hay dependencias circulares entre módulos
- El namespace de cada módulo es único y sigue el patrón `org.role.samples_button.core.<nombre>`
