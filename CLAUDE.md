# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app is

Offline-first Android field-survey app (`pk.gop.pulse.katchiAbadi`, Gradle project name "Katchi Abadi") used by
surveyors in Punjab, Pakistan. A surveyor logs in, picks a **mauza** + **area**, downloads map tiles and the
active parcels for that area, then walks the field surveying parcels on an ArcGIS map — splitting, merging,
drawing, declining and validating crop varieties — all while offline. Everything is written to Room first and
uploaded later from the saved-records screen.

Because the device is usually offline and the surveyor's day of work lives only in the local DB until upload,
**data loss is the worst possible bug here**. That drives most of the conventions below.

## Build commands

Single Gradle module (`:app`). Use the wrapper.

```bash
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # minified + shrunk release APK
./gradlew installDebug             # build + install on connected device
./gradlew clean
./gradlew lint                     # Android Lint -> app/build/reports/lint-results-*.html
./gradlew :app:dependencies        # dependency tree
```

On Windows use `gradlew.bat`. JDK 17 is required (`sourceCompatibility`/`jvmTarget` are both 17).

**There are no tests.** `app/src/test` and `app/src/androidTest` do not exist — only the
`androidJUnitRunner` declaration in `app/build.gradle.kts`. `./gradlew test` succeeds by doing nothing.
Verification is manual: build, install, exercise the flow on a device. Don't claim a change is "tested"
because Gradle went green.

Note `org.gradle.unsafe.configuration-cache=true` in `gradle.properties` — stale-config failures after
editing build scripts usually clear with `./gradlew --no-configuration-cache <task>`.

## Stack

| Concern | Choice |
|---|---|
| Language / build | Kotlin 1.9, AGP 8.4.2, Gradle KTS, JDK 17 |
| Versions & deps | Centralised in `buildSrc/src/main/kotlin/` (`AppConfig`, `Dependencies`, `Versions`, `BuildPlugins`) |
| SDK | compileSdk/targetSdk 34, minSdk 24 |
| DI | Hilt (kapt) |
| Persistence | Room (kapt), DB name `kachi_abadi_db` |
| Network | Retrofit + Gson + OkHttp logging interceptor |
| Maps | **ArcGIS Runtime for Android 100.15.5** (the older Java API, *not* the 200.x Kotlin API) |
| UI | XML layouts + ViewBinding, Navigation component with SafeArgs, no Compose |
| Other | Firebase Crashlytics/Analytics, Glide, ZXing, Dexter, ThreeTenABP, biometric |

ArcGIS resolves from `https://esri.jfrog.io/artifactory/arcgis`, declared in `settings.gradle.kts`.
The runtime licence string is hardcoded at `FragmentMap.kt` and `MapAnalysisActivity.kt` call sites.

### Base URLs live in native code

`Constants.BASE_URL` and friends are empty strings until `MyApplication.onCreate()` fills them from JNI
`external fun`s backed by `app/src/main/cpp/native-lib.cpp` (built via CMake, `externalNativeBuild`).
**To change an endpoint host you edit the C++ file, not Kotlin.** Anything reading `Constants.BASE_URL`
before `Application.onCreate` sees `""`.

Most `ServerApi` methods take a full `@Url` from `Constants`; newer ones use relative paths
(`api/MobileData/...`) against the Retrofit `baseUrl`. Both styles coexist — match whichever the
neighbouring endpoint uses.

## Architecture

Loosely Clean Architecture. Package root `pk.gop.pulse.katchiAbadi`:

```
ui/activities, ui/fragments   Views. Most business logic actually lives here, not in ViewModels.
presentation/                 ViewModels (SharedFormViewModel, MenuViewModel, ...), BaseActivity, util/
domain/model                  Room @Entity classes + their action/status constant objects
domain/repository             Repository interfaces … and NewSurveyRepositoryImpl (see below)
domain/use_case               Use cases, grouped into aggregate holders (SurveyFormUseCase, MenuDataUseCase …)
data/local                    DAOs + AppDatabase + migrations
data/remote                   ServerApi (one big Retrofit interface) + response/ + post/ + request/ DTOs
data/repository               Most repository impls + ParcelActivityLogger
adapter/                      RecyclerView adapters
common/                       Constants, Resource, TileManager, download tasks, converters
di/AppModule.kt               The single Hilt module: OkHttp, Retrofit, DB, DAOs, repos, use cases
```

Two conventions worth knowing before you go looking for a file:

- **Repository impls are split across two packages.** Most `*RepositoryImpl` files are in `data/repository`,
  but `NewSurveyRepositoryImpl` — the most important one — sits in `domain/repository` next to its interface.
- **`AppModule` is the only DI module of substance** (plus `DispatchersModule`). New DAOs/repos get a
  `@Provides` there; nothing uses `@Binds`.

`Resource<T>` (`common/Resource.kt`) is the result wrapper: `Success` / `Error` / `Loading` / `Unspecified`.
`SimpleResource = Resource<Unit>`.

### The four files that carry the app

These are large and dense; read the relevant region rather than the whole file.

| File | ~lines | Role |
|---|---|---|
| `ui/fragments/form/FragmentMap.kt` | 4800 | The map. ArcGIS overlays/symbols, GPS tracking, tile loading, and every parcel-geometry mode: draw, free-polygon, snap/trace (`SnapUtils`/`TraceUtils`), split, merge, task-assign, decline, variety validation. Mode state is a set of `isXMode` booleans — check which modes are active before adding another. |
| `ui/activities/MenuActivity.kt` | 3000 | Home. Bottom-nav tabs, mauza/area selection, tile + parcel download with the stepped progress dialog, sync modes, pending badges (tasks / declines / validations), logout, clear-cache / clear-all-data, theme. |
| `ui/activities/SurveyActivity.kt` | 1900 | The survey form. Crop/type/variety spinners (with custom-entry dialogs), grower & person entries (`PersonEntryHelper`, JK growers), camera capture with GPS + compass bearing stamped on the image, sowing details, edit mode. |
| `domain/repository/NewSurveyRepositoryImpl.kt` | 1800 | Upload pipeline. Turns one saved survey into the right set of server posts, then POSTs and reconciles local parcel state. |

### Parcel operations — the core domain rule

A saved survey carries a `parcelOperation`, and `NewSurveyRepositoryImpl.uploadSurvey()` branches on it:

- `"Split"` → `processSplitParcels()`, and the original parcel id is deactivated after a verified upload
  (`deactivateOriginalParcelWithVerification`)
- `"Merge"` → `processMergeParcels()`
- `"Same"` → `processSameParcels()`
- anything else → `processOtherParcels()`
- a parcel with `isLocallyDrawn = true` short-circuits all of the above and uploads as `New`/`Drawn`

`ActiveParcelEntity` (table `active_parcels`) is the surveyor's working set: `isActivate` hides consumed
parcels, `isLocallyDrawn` marks free-drawn polygons, `variety*` columns hold offline variety validations
pending upload. Geometry travels as WKT strings (`geomWKT`, `centroid`).

If you touch split/merge, keep the deactivation of superseded parcels — dropping it leaves ghost parcels on
the map that the surveyor will survey twice.

### Offline map tiles

`TileManager` stores tiles under `filesDir/MapTiles/<areaId>` (falling back to `cacheDir`).
`Constants.MAP_DOWNLOAD_TYPE` selects `DownloadType.TILES` (current) vs `TPK`.

## Conventions

### Dialogs: `ZdAlert`, never raw `AlertDialog`

`presentation/util/ZdAlert.kt` — four entry points, all rendering `dialog_zd_alert.xml`:

```kotlin
ZdAlert.warn(context, title, message, details = listOf(...))      // amber disc
ZdAlert.error(context, title, message)                            // red disc, "Fix it"
ZdAlert.confirm(context, title, message) { /* onConfirm */ }      // amber + Cancel
ZdAlert.confirmDanger(context, title, message) { /* onConfirm */ }// red + danger button
```

All ZdAlert dialogs are deliberately **non-cancelable** (no outside-tap, no back) so a surveyor cannot
dismiss a warning without reading it. Keep that when adding new ones. Use `confirmDanger` for anything
that deletes or discards local survey data.

### Toasts: `ToastUtil`

`ToastUtil.showShort(context, msg)` / `showLong(...)`, defined in `presentation/util/UiEvent.kt`
(not in a file named after it). Don't call `Toast.makeText` directly.

### Colors

The brand palette, defined in `app/src/main/res/values/colors.xml` and used across layouts and drawables:

| Hex | Role | Resource |
|---|---|---|
| `#2E7D32` | Primary forest green | `colorPrimary` |
| `#1B5E20` | Dark green — status bar, canopy/header gradients | `colorPrimaryDark`, `forest_green_dark`, `srv_canopy_start` |
| `#B47B12` | Amber accent — warnings, highlighted icons | `srv_icon_amber` (`#FFB47B12`) |

Reference the resource name in new layouts; only inline the hex where the surrounding file already does.
The app supports light/dark (`AppCompatDelegate` night mode persisted in the `theme_prefs` prefs file,
applied in `MyApplication` and re-applied per activity) — check the dark variant of any color you add.

### Every parcel operation must be logged via `ParcelActivityLogger`

`data/repository/ParcelActivityLogger.kt` is a `@Singleton` audit trail writing to the `parcel_activity_log`
table. Any code path that creates, changes, uploads, deactivates, declines or validates a parcel logs it:

```kotlin
@Inject lateinit var logger: ParcelActivityLogger

logger.log(parcel, ParcelAction.SPLIT,
    details = "Split into ${newParcels.size} parts: ${newParcels.joinToString { it.subParcelNo }}",
    relatedParcelIds = newParcels.joinToString(",") { it.id.toString() })

// or, without an entity in hand:
logger.log(parcelId, parcelNo, ParcelAction.SURVEY_UPLOAD_FAILED, subParcelNo,
    status = "FAILED", surveyPkId = survey.pkId, details = errorMessage)
```

Rules:

- Use a constant from `ParcelAction` (`domain/model/ParcelActivityLogEntity.kt`). Add a new constant there
  rather than passing a bare string.
- Log failures too, with `status = "FAILED"` and the error in `details` — that's how upload problems get
  diagnosed from a device that was offline all day.
- `log()` is fire-and-forget on the logger's own IO scope and swallows its exceptions, so it is safe to call
  from anywhere and **never** gates the operation it records.
- Screens that know the device position should keep `logger.lastLat` / `logger.lastLng` current
  (`FragmentMap`, `SurveyActivity`) — that's where each log entry's coordinates come from.
- `mauzaId`, `areaName`, `userId`, `userName`, app version and device info are filled from SharedPreferences
  automatically; don't pass them unless overriding.

### Room: never bump the version without a migration

`data/local/AppDatabase.kt`. `AppDatabase.getDatabase()` chains `.addMigrations(...)` and then calls
`.fallbackToDestructiveMigration()` — so a missing or mis-numbered migration does not crash, it **silently
wipes the surveyor's unuploaded work**. Adding a column without a migration is therefore a data-loss bug,
not a build error.

When changing any `@Entity`:

1. Write `val migrationNtoN+1 = object : Migration(N, N+1) { ... }` at the bottom of `AppDatabase.kt`.
2. Register it with `.addMigrations(migrationNtoN+1)` in `getDatabase()`.
3. Bump `version = N+1` in the `@Database` annotation.
4. New tables: `CREATE TABLE IF NOT EXISTS`, plus the same `CREATE INDEX IF NOT EXISTS` statements Room
   would generate for any `@ColumnInfo(index = true)` / `indices = [...]` (see `migration26to27`).
5. New columns must be `NOT NULL DEFAULT <value>` unless the Kotlin property is nullable.

**Version numbering history — read this before bumping.** Versions 22, 23 and 24 were once bumped with no
schema change and no migration, so field devices ended up with `user_version = 24` while their tables were
still schema-21 content. That is now repaired: no-op bridges `migration21to22` / `22to23` / `23to24` fill the
phantom steps, and the three real migrations were renumbered to `migration24to25` (the `active_parcels`
variety columns), `migration25to26` (`mauza_boundary` + `isLocallyDrawn`) and `migration26to27`
(`parcel_activity_log`). The chain is unbroken 1 → 27 and `version = 27` matches it. Keep it that way —
every bump needs a registered migration, even an empty one.

### Corporate Farms: gate on the isCorporateUser flag

The app serves two flows from one build. Corporate Farms surveyors get a farm
hierarchy, lessors and irrigation; everyone else gets the ordinary Katchi Abadi form.

The switch is the server's `Users.isCorperateUser` flag (the typo is the column's, not
ours). `LoginSurveyorResponse` maps it with `@SerializedName` alternates covering the
likely spellings, `LoginFragment` stores it in `SHARED_PREF_IS_CORPORATE_USER`, and all
three gates read that one boolean:

```kotlin
private val isCorporateFarm: Boolean
    get() = sharedPreferences.getBoolean(Constants.SHARED_PREF_IS_CORPORATE_USER, false)
```

**Never gate on group id or unit id.** Corporate and non-corporate users share both:
Corporate Farms sits in `Group_Id = 1` alongside JDW-1/2/3 and DSML. The `GROUP_ID_*`
constants exist only to pick grower-code formats in `PersonEntryHelper`.


Corporate work is additive and isolated so the Katchi Abadi flow cannot regress:

- `CorporateRepository` is separate from `DropdownRepository` (different controllers,
  different cache tables). Both are server-first with local-cache fallback.
- Corporate endpoints live under `MobileDataCorporate` / `CorporateShapefile` /
  `CorporateManagement`. The crop/variety ones carry a `Corporate` prefix in their method
  names (`getCorporateCrops`) because the Katchi Abadi methods own the plain names.
- `cardCorporate` in `activity_survey_new.xml` is `gone` by default; `setupCorporateSection()`
  returns immediately when the gate is false, so nothing loads and every corporate column
  on `new_surveys` stays null.
- **Logout clears the account's area selection, not just the token.**
  `clearLocalDataAndNavigate()` removes the corporate flag, group/unit and every
  selected/downloaded mauza + area key; `handleSessionExpired()` routes through the same
  function. Without this the next user on the device inherits the previous one's selection,
  and after a corporate session that means `mauzaId = 0` with a tehsil name - every parcel
  query then points at the wrong data.
- Corporate surveyors have **no mauza or area** - they pick a **tehsil**. `selectTehsil()`
  writes that tehsil into the existing mauza/area prefs (`mauzaId = 0`, mauzaName =
  areaName = tehsil) so `FragmentMap`, `extentFromArea()` and the survey form keep working
  unchanged. Synced parcels carry `mauzaId = 0` and `areaAssigned = <tehsil>`, which is
  also what `deleteParcelsForTehsilExcept` matches on - there is no `tehsil` column.
- **Corporate property type drives which fields are on screen.** Picking anything other
  than "Crop Survey" hides `layoutCropSurveyFields` and `layoutCorporateIrrigation` via
  `toggleCropSurveyFields()`, which also clears crop type, variety, sowing date and
  irrigation quantity so a stale pick cannot reach the saved survey. The crop-type and
  variety validations check that visibility before demanding a value.
- **Every corporate dropdown comes from a corporate endpoint.** Property type, crops, crop
  types and varieties all branch on the corporate flag: `CorporateRepository` hits
  `GetCorporatePropertyTypes` / `GetCorporateCrops` / `GetCorporateCropTypes` /
  `GetCorporateCropVarieties`, while `DropdownRepository` keeps serving the Katchi Abadi
  lists. Both cache into the same local tables, which is safe because a device only ever
  belongs to one kind of user. `setupSpinners()` still seeds the hardcoded property-type
  pair; `setupCorporateSection()` replaces it once the API answers.
- **The corporate form hides the Katchi Abadi-only sections.** `setupCorporateSection()`
  sets `cardOwners` and `cardFarmerProfile` to `gone` — corporate captures lessors, not
  owners/growers, and has no farmer photo. The submit validation follows the same split:
  corporate requires at least one lessor, everyone else requires at least one owner.
- **`mauzaId = 0` breaks any guard that reads it as "nothing selected".** Corporate parcels
  are stored with `mauzaId = 0` and the tehsil in `areaAssigned`, so checks shaped like
  `if (mauzaId <= 0) showEmpty()` silently blank out for every corporate user — this hit the
  home summary card. The real question is whether an area name is present; branch on the
  corporate flag before treating a zero mauza as unset.
- **The draw tool depends on a boundary being present.** `FragmentMap.loadMauzaBoundary()`
  hides `fabStartDraw` whenever it cannot build a boundary polygon. Corporate boundaries
  live in `aoi_boundaries` keyed by tehsil (never in `mauza_boundary`, and their `mauzaId`
  is 0), so that lookup has to branch on the corporate flag or the editing tools silently
  disappear. Corporate also forces the tool on — every corporate parcel is hand-drawn —
  while Katchi Abadi only shows it in free-polygon sync mode.
- The AOI endpoints return **pipe-delimited text, not JSON**:
  `Tehsil||The_Id||District||Area||WKT` for boundaries and
  `Id||Parcel_No||SubParcelNo||S_Status||AttachedSurveyId||IsDrawn||WKT` for parcels.
  Re-syncing a tehsil keeps every parcel whose survey is still pending (`statusBit = 0`),
  otherwise a sync would wipe the day.
- **Upload branches at the top of `uploadSurvey()`**: corporate goes to
  `uploadCorporateSurvey()` and never touches the Split/Merge/Same pipeline. One survey
  becomes exactly one `CorporateParcelPost` sent to `postCorporateSurveyData`, always with
  `parcelId = 0` and `parcelOperation = "New"` / `"Drawn"` - the corporate server creates a
  fresh row every time. The payload carries `lessors` + `sowingPersons` and sends `persons`
  empty, which is the opposite of the Katchi Abadi payload.

### Networking gotchas in `AppModule.provideOkHttpClient`

- `retryOnConnectionFailure(false)` is **intentional**. Survey uploads are non-idempotent
  POSTs carrying base64 images; OkHttp's silent retry created duplicate server-side records.
  Don't re-enable it.
- Read/write timeouts are 5 minutes for the same reason (multi-MB payloads over rural mobile
  data).
- The auth interceptor must call `chain.proceed()` exactly once — an earlier version called it
  twice and double-posted. Use `response.peekBody()` if you need to inspect a body.
- Debug builds install an all-trusting `X509TrustManager` and a permissive hostname verifier.
  That is `BuildConfig.DEBUG`-only; keep it that way.
- `VersionCheckInterceptor` runs first and can force an update prompt.

### Style notes

- **Write new comments and user-facing strings in English.** Older parts of the codebase carry
  Roman Urdu comments (e.g. *"Sirf button se band ho - bahar tap ya back se nahi"*); leave those
  alone, but do not add more. All Corporate Farms code is English throughout.
- **Line endings are mixed** and there is no `.gitattributes` (git runs with
  `core.autocrlf=true`, so repo content normalises to LF on commit). Some files are CRLF
  (`AppDatabase.kt`, `ActiveParcelEntity.kt`), others are LF (`MenuActivity.kt`,
  `SurveyActivity.kt`). Match whatever the file already uses and never mix the two inside
  one file. `sed -i` here rewrites a whole file to LF, so prefer `perl -i -pe`. Verify with
  a CR byte count (`tr -cd '\r' < f | wc -c`), not `file` — `file` prints "CRLF, LF" for a
  mixed file and a naive `grep CRLF` reads that as clean.
- `AppModule.kt` and several activities contain large commented-out blocks of superseded
  implementations. Leave them alone unless the task is cleanup.
- App version lives in `buildSrc/src/main/kotlin/AppConfig.kt` (`versionCode` / `versionName`),
  not in `app/build.gradle.kts`. `Constants.SURVEY_YEAR` is the current survey season and is
  updated by hand.
