# Mini — launcher de Android minimalista (el más liviano posible)

Un solo archivo de código (`Home.java`), cero dependencias.

## Cómo se usa

- **Inicio:** reloj, fecha y hasta 7 apps favoritas en texto.
- **Abrir el cajón de apps:** deslizá hacia arriba, o tocá "Todas las apps".
- **Buscar:** tocá la barra de arriba del cajón y escribí (ignora tildes y mayúsculas). *Enter* abre el primer resultado. El teclado no se abre solo, a propósito.
- **Fijar / quitar un favorito:** mantené apretada una app en el cajón (para fijarla) o en el inicio (para quitarla).
- **Atrás / botón Home:** vuelven al inicio limpio.

## Compilar e instalar

1. Abrí la carpeta en **Android Studio** (hace falta el SDK de Android 36; Studio lo ofrece instalar).
2. `Build > Select Build Variant` → **release** (con R8 y recursos reducidos). El debug no está optimizado y no sirve para medir RAM.
3. `Run`, o por línea de comandos: `gradle wrapper` una vez y después `./gradlew assembleRelease`.
   El APK queda en `app/build/outputs/apk/release/`.
4. Instalalo y apretá Home: Android te deja elegir **Mini** como app de inicio.

## Compilar con GitHub (sin instalar nada en la PC)

1. Creá un repositorio nuevo en GitHub y subí el contenido de esta carpeta (rama `main`).
2. Entrá a la pestaña **Actions**: el workflow `Build APK` corre solo con cada push (o con *Run workflow*).
3. Cuando termine, abrí la corrida y bajá el APK de **Artifacts** (`Mini-release-apk`).
4. Para tener un link directo de descarga: creá un tag `v1.0` y el APK aparece en **Releases**.

## Por qué gasta poca RAM

| Decisión | Efecto |
|---|---|
| Sin AndroidX / AppCompat / Material / Compose / Kotlin | No se cargan cientos de clases de librerías; solo el framework, que ya está en memoria del sistema |
| UI armada en código, sin XML de layouts | No se infla ni se parsea nada |
| Solo texto, **ningún ícono** | Los bitmaps de íconos son lo que más memoria usa en cualquier launcher |
| La lista de apps solo existe con el cajón abierto | Se arma al abrir y se libera al cerrar (también al irse a segundo plano) |
| Sin servicios, receivers ni trabajo en segundo plano | Nada corre mientras no lo usás; la lista se reconstruye en cada apertura, así que no hace falta escuchar instalaciones |
| `hardwareAccelerated="false"` | Sin contexto de GPU ni RenderThread para una UI que es solo texto |
| Fondo negro opaco, sin wallpaper propio | Menos para componer y dibujar (y ahorra batería en OLED) |
| R8 + `shrinkResources` en release | APK y dex mínimos |

## Medir

Con el launcher en uso y el teléfono conectado por USB:

```
adb shell dumpsys meminfo app.minilauncher
```

Mirá la fila **TOTAL PSS** (ideal: con el cajón cerrado, después de un rato de uso).

## Ajustes rápidos

- **Scroll poco fluido** en el cajón → en `AndroidManifest.xml`, `android:hardwareAccelerated="true"`.
- **Cantidad de favoritos** → `MAX_FAVS` en `Home.java`.
- **Tamaños y colores** → constantes en `buildHome()` / `openDrawer()`.
