# Onboarding Tutorial — Design Spec

**Date:** 2026-04-23
**Status:** Approved

---

## Resumen

La primera vez que el usuario abre la app se muestra un pager de bienvenida de 5 páginas que explica el flujo completo. Puede omitirse en cualquier momento. Una vez completado o saltado, no vuelve a aparecer automáticamente, pero puede relanzarse desde el menú de la pantalla principal.

---

## Formato

**HorizontalPager** de pantalla completa, ruta dedicada en el NavHost (`"onboarding"`).

- Barra de puntos indicadores de página en la parte inferior.
- Botón "Omitir" (TextButton) en la esquina inferior izquierda en todas las páginas.
- Botón "Siguiente →" (FilledButton) en la esquina inferior derecha en páginas 1-4.
- En la página 5 el botón cambia a "¡Empezar!" (primera ejecución) o "Cerrar" (relanzado).

---

## Las 5 páginas

| # | Título | Icono | Contenido |
|---|--------|-------|-----------|
| 1 | Bienvenido a SoundBoard | 🎛️ | Descripción breve de la app: organiza audios en grupos y dispáralos al instante |
| 2 | Crea un grupo | 📁 | Toca ＋ para crear un grupo (ej: "Percusión", "Efectos"). Muestra el FAB. |
| 3 | Añade samples | 🎵 | Dentro de un grupo toca "Añadir sample" para explorar tus archivos de audio |
| 4 | Reproduce | ▶️ | Toca ▶ para reproducir. Usa la barra de progreso para saltar a cualquier punto |
| 5 | Controles extra | ⚙️ | 🔁 Bucle repite el sample · ↩️ Restart vuelve al inicio · ↕️ Drag & drop para reordenar |

---

## Arquitectura

### Archivos nuevos

```
core/data/src/main/java/.../
  UserPreferencesRepository.kt         ← interfaz: hasSeenOnboarding: Flow<Boolean>, markSeen()
  UserPreferencesRepositoryImpl.kt     ← implementación con DataStore<Preferences>
  di/PreferencesModule.kt              ← Hilt: provee DataStore y el repositorio

feature/onboarding/impl/src/main/java/.../
  OnboardingScreen.kt                  ← HorizontalPager de 5 páginas, stateless
```

### Archivos modificados

```
app/build.gradle                       ← dependencia :feature:onboarding:impl
feature/onboarding/impl/build.gradle   ← módulo nuevo
libs.versions.toml                     ← androidx.datastore:datastore-preferences
MainActivity.kt                        ← añade composable("onboarding") al NavHost
SoundBoardViewModel.kt                 ← inyecta UserPreferencesRepository, expone hasSeenOnboarding
SoundBoardScreen.kt                    ← LaunchedEffect + item "Ver tutorial" en menú ⋮
```

### Dependencia de DataStore

```toml
# libs.versions.toml
datastore = "1.1.1"
androidx-datastore = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

---

## Flujo de datos

```
DataStore(has_seen_onboarding)
  → UserPreferencesRepository.hasSeenOnboarding: Flow<Boolean?>
  → SoundBoardViewModel.hasSeenOnboarding: StateFlow<Boolean?>
  → SoundBoardScreen: LaunchedEffect → navigate("onboarding") si false
```

`StateFlow` emite `null` mientras DataStore carga. El `LaunchedEffect` solo actúa cuando el valor es `false` confirmado, evitando un flash de navegación.

---

## Navegación

### NavHost (MainActivity)

```kotlin
composable("onboarding?firstRun={firstRun}",
    arguments = listOf(navArgument("firstRun") {
        type = NavType.BoolType; defaultValue = false
    })
) { backStack ->
    val isFirstRun = backStack.arguments?.getBoolean("firstRun") ?: false
    OnboardingScreen(
        isFirstRun = isFirstRun,
        onFinish = { navController.popBackStack() }
    )
}
```

- Cuando `SoundBoardScreen` detecta primera vez navega a `"onboarding?firstRun=true"`.
- Cuando el menú "Ver tutorial" lo lanza navega a `"onboarding"` (defaultValue = false).
- `startDestination` permanece `"soundboard"`. El `LaunchedEffect` en `SoundBoardScreen` dispara la navegación si es primera vez.

### Primera ejecución

1. App abre → `SoundBoardScreen` monta.
2. `hasSeenOnboarding` emite `false` → `LaunchedEffect` navega a `"onboarding"`.
3. Usuario navega las 5 páginas.
4. Al tocar "¡Empezar!" o "Omitir" → `markSeen()` escribe `true` en DataStore → `popBackStack()`.
5. Próximos arranques: `hasSeenOnboarding` emite `true` → no navega.

### Relanzar desde menú

1. Usuario toca ⋮ en `SoundBoardScreen` → item "Ver tutorial".
2. Navega a `"onboarding"` directamente (DataStore no se modifica).
3. `OnboardingScreen` recibe `isFirstRun = false` → botón final es "Cerrar".
4. Al cerrar → `popBackStack()`.

---

## OnboardingScreen — contrato

```kotlin
@Composable
fun OnboardingScreen(
    isFirstRun: Boolean,
    onFinish: () -> Unit,       // popBackStack en todos los casos
    onMarkSeen: () -> Unit      // llamado solo si isFirstRun = true
)
```

- Stateless respecto a navegación; la lógica de DataStore queda en el ViewModel/repositorio.
- `onMarkSeen` se llama antes de `onFinish` cuando `isFirstRun = true`.

---

## Persistencia

- **DataStore<Preferences>** en `core/data`.
- Clave: `booleanPreferencesKey("has_seen_onboarding")`.
- Valor por defecto: `false` (si la clave no existe).
- No se resetea al relanzar desde el menú.

---

## Qué NO incluye este spec

- Animaciones entre páginas (usa el comportamiento por defecto de HorizontalPager).
- Localización a otros idiomas (la app ya está en español).
- Tests de UI con Compose Testing (fuera del alcance inicial).
