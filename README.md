# Netflix- (Streaming App, CMS App, Backend)

Monorepo with three parts:
- **Streaming app** (`streming-app/`): Android (Jetpack Compose) client for browsing, profiles, playback, downloads, and progress sync.
- **CMS app** (`cms-app/`): Android (Jetpack Compose) admin tool to upload/edit movies, thumbnails, and metadata.
- **Backend** (`jersey-jetty/`): Jetty/Jersey REST API + media streaming + MariaDB persistence (optional GCS storage for videos/thumbnails).

## Architecture
```
CMS App --> Backend (REST) --> Storage (local or GCS)
Streaming App --> Backend (REST/stream) --> Storage
```
- Profiles and auth live on the backend; streaming app signs in then selects profiles.
- Movies store `videoPath` and `thumbnailPath`; clients use those URLs to stream.
- Progress is tracked per profile and synced with the backend; local downloads also supported.

## Requirements
- Android Studio Flamingo+ (JDK 17 toolchain) for both apps.
- MariaDB/MySQL for backend DB.
- (Optional) Google Cloud Storage credentials for remote storage; otherwise local filesystem paths under `./res/videos` and `./res/thumbnails`.

## Build & Run
### Streaming app
1) Open `streming-app/` in Android Studio.
2) Set the API base URL in `streming-app/src/main/java/com/example/netflix/network/RetrofitInstance.kt` (default `http://10.0.2.2:8080/` for emulator).
3) Run the `app` configuration on an emulator/device.

### CMS app
1) Open `cms-app/` in Android Studio.
2) Set the API base URL in `cms-app/src/main/java/com/example/cms_app/network/RetrofitInstance.kt`.
3) Run the `app` configuration.

### Backend
1) Configure DB connection in `jersey-jetty/src/main/java/com/mariadb/Mariadb.java`.
2) Start Jetty (example):
   ```bash
   cd jersey-jetty
   mvn clean package
   java -jar target/jersey-jetty-*.jar
   ```
   or use the run script in `jersey-jetty/run server.txt`.
3) For GCS, set credentials and bucket in `jersey-jetty/src/main/java/com/mkyong/GCSHelper.java`.

## Key Endpoints (backend)
- Auth: `POST /user/connect`, `POST /user` (create)
- Movies: `GET /movie/all`, `GET /movie/{id}`, `POST /movie`, `PUT/PATCH /movie/{id}`, `DELETE /movie/{id}`
- Files: `POST /file/upload` (video), `POST /file/upload-thumbnail`, `GET /movie/thumbnails/{name}`
- Profiles: `GET/POST/PATCH/DELETE /profiles`
- Progress/Ratings: see `Ratings.java` and `ProgressRepository`
- Streaming: `GET /stream/{movieName}` (range supported via `MediaStreamer`)

## Data Model (core)
- **Movie**: id, name, description, `genre` (bitmask), year, `videoPath`, `thumbnailPath`
- **Profile**: id, userId, name, avatarColor (drawable seed), kids flag
- **Progress/Rating**: movieId, profileId, positionMs (progress), rating value

## Notable Behaviors
- Genres are a bitmask; UI maps to readable names and supports multi-select in CMS.
- Streaming app shows continue-watching time and lets users pick quality (1080p/360p).
- If no thumbnail exists, the streaming UI uses a blue fallback background.
- CMS upload flow: upload video -> optional thumbnail (or backend default) -> save metadata with returned paths; edits update existing rows.
- Deleting a movie removes DB row and (when configured) calls GCS helper to delete stored objects.

## Customization Tips
- Update base URLs in both `RetrofitInstance.kt` files when switching between local and remote servers.
- Add avatar drawables in `streming-app/src/main/res/drawable` (named `avatar1.png`, `avatar2.png`, …) to expand profile choices.
- To change the app name/icon, edit `streming-app/src/main/AndroidManifest.xml` and corresponding resources in `res/values/strings.xml` and `res/mipmap-*`.

## Troubleshooting
- Emulator cleartext issues: ensure `network_security_config.xml` allows `10.0.2.2` or use HTTPS.
- Upload 500/405 errors: verify backend endpoints (`UploadService` for files, `Movies` for metadata) and storage permissions/paths.
- Only one movie playable: check that `videoPath` stored in DB points to the actual uploaded file; clients rely on that path, not the movie name.
