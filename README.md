<p align="center">
  <img src="docs/branding/mintfy-banner.png" alt="Mintfy - See your money clearly" width="100%">
</p>

<p align="center">
  <img src="docs/branding/mintfy-icon.png" alt="Mintfy app icon" width="96">
</p>

<h1 align="center">Mintfy</h1>

<p align="center">
  A personal finance app for people with irregular, multi-source income and a goal they can't see moving.
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Firebase" src="https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white">
</p>

---

## 1. Project Overview

**Course:** SE3092 Platform Based Development, Assignment 01 (Year 3, Semester 2, 2026)
**Package:** `com.mintfy.app`

Mintfy is built around one persona, **Kavindu Silva**, a 25-year-old junior software engineer in Colombo.
He earns from four sources (salary, freelance projects, Google AdSense in USD, and crypto trading) but
cannot say where his money goes. He wants to buy a MacBook Pro M4 (about LKR 490,000) within 12 months
and has LKR 11,200 saved.

Four earlier tools failed him: a Google Sheet, a USD-only budgeting app, a notes file, and his bank's
spending summary. Mintfy is designed to avoid the reasons they failed.

### Problems Mintfy addresses

| Kavindu's problem (from the scenario) | How Mintfy responds |
|---|---|
| Four income sources, different currencies and cycles | _TODO: multi-source income model with per-record currency and LKR value_ |
| Cannot recall what he actually earned | _TODO: monthly income summary based on recorded receipts, not estimates_ |
| Forgotten freelance invoices | _TODO: invoice status tracking_ |
| Expenses scattered across card, cash and subscriptions | _TODO: quick-add expense entry_ |
| No idea of discretionary vs committed spending | _TODO: committed vs discretionary breakdown_ |
| MacBook goal feels abstract | _TODO: goal progress with required monthly savings_ |

### Core features

_TODO: list the features your team actually builds, and mark each as Must / Should / Later._

---

## 2. Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin (100%), Gradle Kotlin DSL |
| UI | Jetpack Compose, Material Design 3 |
| Navigation | Navigation Compose (single activity) |
| Architecture | MVVM (UI → ViewModel → Repository → Model) |
| State | StateFlow, Kotlin Coroutines |
| Dependency injection | Hilt |
| Auth | Firebase Authentication (email/password) |
| Database | Cloud Firestore with real-time listeners |
| Local cache | Room |
| SDK | minSdk 26 (Android 8.0), targetSdk 34+ |

---

## 3. Architecture Summary

```
UI (Composables)  →  ViewModel (StateFlow)  →  Repository  →  Firestore / Room
```

- **UI layer:** Composables only render state and send events. No business logic.
- **ViewModel layer:** Holds screen state as `StateFlow` and calls repositories inside `viewModelScope`.
- **Repository layer:** The single source of truth. Combines Firestore (remote) and Room (offline cache).
- **Model layer:** Plain Kotlin data classes for income, expenses, categories and goals.

_TODO: add the MVVM diagram from `docs/` and a short note on package structure._

### Package structure

```
com.mintfy.app
├── data        (repositories, Firestore and Room sources, DTOs)
├── domain      (models, use cases)
├── di          (Hilt modules)
└── ui          (screens, components, theme, navigation, viewmodels)
```

_TODO: adjust to match the final structure._

---

## 4. Firebase Configuration

1. Create a project in the [Firebase Console](https://console.firebase.google.com).
2. Add an Android app with the package name `com.mintfy.app`.
3. Download `google-services.json` and place it in `app/`.
   This file is listed in `.gitignore` and is **not** committed.
4. Enable **Authentication → Sign-in method → Email/Password**.
5. Create a **Cloud Firestore** database.
6. Deploy the security rules from [`firestore.rules`](firestore.rules):
   ```bash
   firebase deploy --only firestore:rules
   ```
7. _TODO: add any composite indexes (see `firestore.indexes.json`)._

---

## 5. Build Instructions

**Requirements:** Android Studio (latest stable), JDK 17, an emulator or device on Android 8.0+.

```bash
git clone https://github.com/<your-org>/mintfy-android.git
cd mintfy-android
# add app/google-services.json (see section 4)
./gradlew assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 6. Documentation

| Document | Location |
|---|---|
| Requirements (SRS) | `docs/` _TODO_ |
| Use case diagram | `docs/` _TODO_ |
| Firestore schema | `docs/` _TODO_ |
| MVVM architecture diagram | `docs/` _TODO_ |
| Annotated wireframes | `docs/` _TODO_ |
| Firestore security rules | [`firestore.rules`](firestore.rules) _TODO_ |

---

## 7. Team

| Name | Index no. | Area |
|---|---|---|
| _TODO_ | _TODO_ | _TODO_ |
| _TODO_ | _TODO_ | _TODO_ |
| _TODO_ | _TODO_ | _TODO_ |

---

## 8. Branding

Palette: near-black `#0A0A0A`, white, and leaf green `#8FD47A`. Logo files are in
[`docs/branding/`](docs/branding).
