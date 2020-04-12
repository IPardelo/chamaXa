<div align="center">

<img src="assets/banner.svg" alt="ChamaXa" width="720">

<br>

**Un botón, unha chamada: o teléfono máis sinxelo para os maiores da casa**

Unha pantalla de inicio para Android con botóns grandes de cores que chaman directamente
á familia. Sen menús, sen iconas e sen maneira de perderse.

<br>

![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-5.0+-3DDC84?style=flat-square&logo=android&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.11-02303A?style=flat-square&logo=gradle&logoColor=white)
![Internet](https://img.shields.io/badge/Internet-non%20precisa-555555?style=flat-square)
![Version](https://img.shields.io/badge/version-0.1.0-e9b44c?style=flat-square)

</div>

## Por que

Precisaba dun teléfono que a miña avoa puidese usar soa. Un smartphone normal está cheo de
iconas, menús, notificacións e avisos que aparecen sen pedilos, e calquera toque de máis
acaba nunha pantalla da que non sabe saír. Os teléfonos "para maiores" son caros e
tampouco se adaptan á familia de cada un. 'ChamaXa' é o contrario: botóns grandes cos
nomes (e as caras) da xente da casa, e un toque para chamar.

## Configuración

### Requisitos

| Requisito | Detalle |
|---|---|
| **Android 5.0+** | Con tarxeta SIM para chamar (probado nun Moto E de 2ª xeración) |
| **Android Studio** | Só para compilar e instalar a app (o JDK 17 xa vén incluído) |

### ▶️ Compilar e probar

1. En Android Studio: *File → Open* → a carpeta do proxecto (a que ten `settings.gradle.kts`).
2. Activa a *depuración USB* no móbil, conéctao e preme **▶ Run**.

## Estrutura xeral

```
chamaXa/
├── settings.gradle.kts                   # Proxecto e módulos
├── build.gradle.kts                      # Versións de AGP e Kotlin
├── gradle.properties                     # Opcións de Gradle
├── gradle/wrapper/                       # Gradle 8.11.1
│
├── app/
│   ├── build.gradle.kts                  # SDK (minSdk 21, targetSdk 35) e dependencias
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml           # Permisos, tema e pantalla de inicio
│       └── res/
│           ├── drawable/ic_launcher.xml  # Icona (grella de cores)
│           ├── values/strings.xml        # Inglés (idioma por defecto)
│           └── values/themes.xml         # Tema escuro a pantalla completa
│
└── assets/                               # Banner e capturas do README
```

## Evolución por versión

### v0.1.0
- Proxecto Android con Kotlin e Jetpack Compose (Material 3), `minSdk 21` (Android 5.0).
- Tema escuro a pantalla completa e icona provisional.

## Folla de ruta

- [ ] Configurar os contactos desde o propio móbil, sen recompilar
- [ ] Icona definitiva (*adaptive icon*) en `mipmap`

## Autor

[Ismael Castiñeira](https://ipardelo.es)

```bash
VIVA GHALISIA E A COSTA DA MORTE! 💀
```
