# Koya Launcher

<p align="center">
  <img src="screenshots_automation/google_play/play_store_icon.png" width="160" height="160" alt="Koya Launcher Logo" style="border-radius: 20%;" />
</p>

<p align="center">
  <a href="https://github.com/lagosproject/koya-launcher/actions"><img src="https://github.com/lagosproject/koya-launcher/actions/workflows/ci.yml/badge.svg" alt="Android Build" /></a>
  <a href="#license"><img src="https://img.shields.io/badge/License-Pending-lightgrey.svg" alt="License: Proprietary" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-1.9.0-purple.svg" alt="Kotlin" /></a>
</p>

Koya Launcher is a distraction-free, minimalist Android home launcher designed to help you regain focus while still allowing you to access a single, essential widget at the top of your screen.

---

## Table of Contents
- [About the Project](#about-the-project)
- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Contributing](#contributing)
- [License](#license)

---

## About the Project
Modern smartphones are packed with widgets, badges, and notification lists that constantly compete for your attention. Koya Launcher simplifies your screen down to:
- A clean time & date header.
- A single widget slot (long press to add).
- An optional battery level bar.
- 4 customizable text shortcuts at the bottom of the home screen.
- A fast, diacritic-insensitive app drawer accessible by swiping or clicking.

---

## Key Features
- **Anti-Distraction UI:** Clean interface with zero icon badges, distracting wallpapers, or visual clutter.
- **Rotation-Locked Layout:** Forced portrait orientation for consistency.
- **Widget Slot:** Long press to select and host any standard Android app widget.
- **Optimized Search:** Pre-computed diacritical mark normalization for instant, lag-free search typing.
- **Multilingual:** Built-in localization support for **English**, **Spanish** (Español), and **French** (Français).

---

## Prerequisites
- Android device running Android 10+ (API Level 29 or higher).
- Android Studio Iguana+ (or latest command-line tools).
- Java Development Kit (JDK) 17.

---

## Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/lagosproject/koya-launcher.git
   cd koya-launcher
   ```

2. **Open the project in Android Studio.**

3. **Build and install on a connected device:**
   ```bash
   ./gradlew installDebug
   ```

---

## Configuration
Before running a production or release build, copy the `.env.example` file to `.env` and fill in your signing certificates:
```bash
cp .env.example .env
```

---

## Contributing
Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

---

## License
License pending Play Store publishing decision. Copyright (c) 2026.
