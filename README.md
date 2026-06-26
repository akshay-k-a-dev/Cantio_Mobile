# Cantio Music Player

Cantio is a premium, modern Android music player built with Kotlin, Jetpack Compose, and Media3. It offers a rich, immersive user experience with neon-tinted dark aesthetics, seamless background audio playback, queue management, and advanced features like smart crossfade.

## Features

- **Rich Material 3 Design**: Visually stunning dark mode interface with neon violet accents, smooth transitions, and premium glassmorphic UI components.
- **Smart Queue Management**: Easily play songs from search results, home recommendations, playlists, and library. Custom queue controls allow adding items to queue, playing next, or reordering.
- **Seamless Media3 Playback**: Integrates with Android's Media3 library and `MediaLibraryService` to support background audio playback, system notification integration, and locking-screen controls.
- **Dynamic Stream Resolution**: Resolves high-quality streams dynamically utilizing a custom multi-client YouTube/YouTube Music fallback mechanism.
- **Smart Crossfade & Analysis**: Analyzes track loudness and structures, performing gapless playback or volume fading when transitioning between tracks.
- **Deep Linking**: Instantly launch specific tracks, public playlists, or shared blends from shared Vercel and Akshaya web URLs.

## Project Structure

- `app/src/main/kotlin/com/appplayer/music`: Contains UI screens, view models, and DI setup.
- `app/src/main/kotlin/com/appplayer/music/playback`: Code for `MusicService`, `PlayerConnection`, and Media3/ExoPlayer integration.
- `app/src/main/kotlin/com/appplayer/music/utils`: Audio analysis, caching manager, and player utility classes.
- `innertube/`: Modular library implementing InnerTube client integrations.

## Getting Started

### Prerequisites

- Android SDK 35 (Compile SDK) / Min SDK 26
- Java Development Kit (JDK) 21
- Gradle 8.5+

### Build & Run

To build and run the debug version of the app on a connected device:

```bash
./gradlew installDebug
```

To build a release production APK:

```bash
./gradlew assembleRelease
```

The resulting production APK will be compiled into the `dist/` or `app/build/outputs/apk/release/` directory.

## References & Acknowledgements

This mobile application is built to extend and sync with the main Cantio ecosystem:

- [Cantio Web (akshay-k-a-dev/Cantio)](https://github.com/akshay-k-a-dev/Cantio)

## License

This project is licensed under the [MIT License](LICENSE).
