# PaisaTrail

A free, open-source, offline-first expense tracker for Android, built for how people in India actually pay: UPI screenshots, bank-statement PDFs, shared bills and EMIs.

No account. No bank login. No SMS access. No ads. No analytics of its own. MIT licensed.

PaisaTrail is an independent project. It is not affiliated with, endorsed by, or derived from the code of any other expense-tracking app.

## Features

| Area | What it does |
|---|---|
| **Share-to-Track** | Share a UPI or bank payment-success screenshot to PaisaTrail from any app. Text is read on the phone (OCR), and the amount, merchant, date and time, reference id and paid-from account are pulled out and logged. Duplicates are rejected by reference id. If something can't be read, you get a short form to fix it. |
| **Statement import** | Pick a one-month bank statement PDF (password-protected PDFs are supported). A bank-agnostic parser finds the rows; you review, edit, untick and confirm before anything is saved. |
| **Auto-categories** | Offline keyword rules sort spends into Food, Shopping, Travel, Grocery, Rent, Investments, Health, EMI/Bill, Subscriptions and Others. When you correct a category, that merchant is remembered. |
| **Dashboard** | Month switcher, monthly total, change vs last month, donut chart with legend, budget bar with amount left, "% income used", and a pull-up sheet of transactions with type and category filters. |
| **Insights** | Daily average, projected month-end spend and a pacing message, all computed locally. |
| **Split & settle** | Unlimited friends and groups, equal or custom splits, who-owes-what chart, Friends / Groups / Settled tabs, and settle-up with a generated UPI QR code and pay link. |
| **Planner** | Monthly budget and income, subscriptions, EMIs, credit cards with limit and % used, and due-date reminders. |
| **Calculators** | FD / RD maturity with selectable compounding, and an old-vs-new-regime income-tax estimator (estimate only). |
| **Your data** | Full backup and restore as one readable JSON file, plus CSV export of transactions. |
| **Look and feel** | Glass-style cards, a floating glass tab bar and a slowly drifting colour backdrop. Dark by default, with light and match-phone options. |
| **Motion** | Spring-driven screen transitions, cascading entrances, numbers that count up, a chart that sweeps in, press-bounce on everything tappable, and light haptics. One "Reduce motion" switch in Settings stills all of it. |

Optional: paste your own Gemini API key in Settings for help categorising merchants the rules can't place, and for a short tip on the Insights card. Everything works without it.

## Get the APK

There is no hosted build. Once the project is on GitHub, the included workflow (`.github/workflows/android.yml`) builds one for you:

- **Every push to `main`**: open the repository's **Actions** tab, open the latest run, and download **PaisaTrail-debug-apk** under *Artifacts*.
- **Every tag starting with `v`** (for example `git tag v1.0.0 && git push --tags`): the APK is attached to a GitHub **Release**, which gives you a permanent download link.

These are debug-signed builds, and each CI run uses a fresh debug key. To install a newer build over an older one you may have to uninstall first, so export a backup from Settings before you do. For builds you intend to hand to other people, set up your own release keystore.

## Build it yourself

Requirements: Android Studio (2024.3 "Meerkat" patch 1 or newer) or JDK 17 with the Android SDK (platform 36).

```bash
git clone https://github.com/<you>/PaisaTrail.git
cd PaisaTrail
./gradlew assembleDebug          # APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

Or open the folder in Android Studio and press Run.

Toolchain: AGP 8.9.1, Gradle 8.11.1, Kotlin 2.0.21, compile/target SDK 36, min SDK 26. All versions are in `gradle/libs.versions.toml`.

The first build writes the Room schema to `app/schemas/`. Commit that folder; it is what future database migrations are tested against.

## How to use Share-to-Track

1. Make a payment in any UPI or banking app.
2. On the payment-success screen tap **Share** (or take a screenshot and share it from your gallery).
3. Choose **Log in PaisaTrail** in the share sheet.
4. PaisaTrail reads the image on your phone and shows a confirmation card with what it logged. From there you can fix details or undo.

You can also tap **+** on Home and pick a screenshot you already have. Sharing a PDF opens statement import instead.

If the same payment is shared twice, the second one is recognised by its reference id and skipped.

## Privacy

- All data lives in an on-device database. There is no server, no account and no sync.
- Android cloud backup and device-to-device transfer are switched off for this app. Moving your data is your call, through Settings > Export backup.
- Permissions requested: **notifications** (only when you switch on due-date reminders) and **internet**. There is no SMS, contacts, location, camera or storage permission. Files are read only when you share or pick them.
- PaisaTrail's own code uses the network for one thing: the optional Gemini calls, which happen only after you save your own API key. Categorising a shared payment the offline rules can't place sends that merchant's name; the insights tip sends monthly totals per category. Nothing else is sent, and the key is stored on the phone and left out of backups.
- OCR uses Google's ML Kit with a model bundled inside the APK, so images are processed on the device. Be aware that the ML Kit library itself can send Google diagnostic and usage metrics about the library. That is ML Kit's behaviour, not something PaisaTrail adds.

**Want a build that cannot go online at all?** Add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` tag in `app/src/main/AndroidManifest.xml` and replace the internet permission line with:

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

The Gemini option then stops working; everything else is unaffected.

**F-Droid / no Google libraries:** OCR sits behind the `OcrEngine` interface (`data/ocr/OcrEngine.kt`). Write a Tesseract-backed implementation, change the single `@Binds` line in `di/AppModule.kt`, and drop the ML Kit dependency.

## Architecture

Single Gradle module, three layers, dependencies pointing inward:

- **`domain/`** is plain Kotlin with no Android imports: money formatting, the screenshot and statement parsers, the categoriser, split maths, calculators, insights and tax slabs. This is where the unit tests are.
- **`data/`** holds Room (entities, DAOs, database), DataStore settings, repositories, the OCR engine, PDF text extraction, backup/restore and the optional Gemini client.
- **`ui/`** is Jetpack Compose with Material 3: one ViewModel per screen exposing `StateFlow`, Navigation-Compose for routing, and a Canvas-drawn donut chart.

Hilt wires it together (`di/AppModule.kt`). WorkManager runs the daily due-date check (`work/ReminderWorker.kt`). To keep the code small, Room entities are used directly as the app's models rather than being mapped to separate domain classes.

Money is stored as paise in `Long` columns everywhere, so totals and splits never drift. Tax slabs and limits are in one file, `domain/calc/TaxSlabs.kt`, with the financial year stated at the top.

```
PaisaTrail/
├── .github/workflows/android.yml        CI: tests, debug APK, release on tags
├── gradle/libs.versions.toml            every dependency version
├── build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                     icons, strings, window theme, backup rules
        │   └── java/com/paisatrail/
        │       ├── MainActivity.kt      single activity + share target
        │       ├── PaisaTrailApp.kt
        │       ├── di/AppModule.kt
        │       ├── domain/
        │       │   ├── model/Models.kt
        │       │   ├── util/            Money, UpiLink, DueDates
        │       │   ├── parse/           UpiScreenshotParser, StatementParser, DateParsers
        │       │   ├── categorize/Categorizer.kt
        │       │   ├── split/SplitCalculator.kt
        │       │   ├── insights/InsightsCalculator.kt
        │       │   └── calc/            FdRdCalculator, TaxCalculator, TaxSlabs
        │       ├── data/
        │       │   ├── local/           Entities, Daos, AppDatabase
        │       │   ├── repo/            Transaction, Split, Planner, Backup repositories
        │       │   ├── prefs/SettingsStore.kt
        │       │   ├── ocr/             OcrEngine, MlKitOcrEngine
        │       │   ├── pdf/PdfTextExtractor.kt
        │       │   ├── ai/GeminiClient.kt
        │       │   └── share/ShareInbox.kt
        │       ├── ui/
        │       │   ├── AppRoot.kt       navigation graph, bottom bar, + sheet
        │       │   ├── theme/
        │       │   ├── components/      Glass, Motion, DonutChart, QrCode, shared widgets
        │       │   ├── home/            dashboard
        │       │   ├── txn/             add / edit a transaction
        │       │   ├── capture/         Share-to-Track
        │       │   ├── importer/        statement import and review
        │       │   ├── split/           friends, groups, add expense, settle up
        │       │   ├── planner/, calc/, settings/
        │       └── work/ReminderWorker.kt
        └── test/java/com/paisatrail/domain/   unit tests
```

## Glass and motion

The visual layer lives in two files, so it is easy to restyle or strip out:

- `ui/components/Glass.kt` has `AuroraBackground` (the drifting backdrop drawn once behind the whole app), `Modifier.glass` (translucent pane with a bright rim) and `Modifier.tintedGlass`.
- `ui/components/Motion.kt` has the reusable animation pieces: `bouncyClickable`, `riseIn`, `popIn`, `shimmer`, `breathe`, `AnimatedAmount`, and `LocalReduceMotion`.

Every decorative animation checks `LocalReduceMotion`, which follows the "Reduce motion" switch in Settings. The backdrop repaints continuously while a screen is visible, so switching it off also saves some battery.

The glass is built from translucency, gradients and rim highlights, which work on every supported Android version. It does not blur the content behind a pane in real time; surfaces that sit over text (tab bar, bottom sheets) are tinted more heavily instead so they stay readable.

## Tests

`./gradlew testDebugUnitTest` runs JVM unit tests for the UPI screenshot parser, statement-row parser, categoriser, split-balance maths, FD/RD and tax calculators, and the money, UPI-link and due-date helpers.

## Known limits

- Statement parsing is heuristic. It handles the common "date, narration, debit/credit, balance" layouts, but an unusual layout may misread rows; that is what the review screen is for. Scanned, image-only PDFs have no text to read.
- Only one-month statements are accepted.
- A shared PDF is always treated as a statement, not as a single receipt.
- There is no in-app camera; share a screenshot or pick one.
- Split tracks what each friend owes you or you owe them. Debts between two friends inside a group are not tracked or simplified.
- The tax estimator covers resident individuals under 60 and leaves out capital gains, special-rate income and surcharge marginal relief. It is an estimate, not tax advice.
- Reminders are checked about once a day by WorkManager; battery-saver settings can delay them.
- Glass panes do not blur what is behind them in real time (see "Glass and motion").
- There are no instrumented (on-device) UI tests yet.

## Making it yours

- **App name:** `app/src/main/res/values/strings.xml`.
- **Package / application id:** `namespace` and `applicationId` in `app/build.gradle.kts`, then rename the `com.paisatrail` package in Android Studio (Refactor > Rename).
- **Icon:** `app/src/main/res/drawable/ic_launcher_foreground.xml`.
- **Category rules:** `domain/categorize/Categorizer.kt`.
- **New tax year:** `domain/calc/TaxSlabs.kt`.

## Contributing

Issues and pull requests are welcome. Parser fixes are especially useful: include the (anonymised) text of the row or screenshot that was misread and add a test for it.

## Licence

MIT. See [LICENSE](LICENSE).
