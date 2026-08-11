# Updater Application

A feature-rich, OneUI-styled (SESL 8) Custom ROM OTA Update application for Android. Designed specifically for custom ROMs (e.g., LichUI / OneUI ports), featuring background update checks, MD5 checksum verification, and dynamic OpenRecoveryScript generation for seamless installation.

---

## 📋 Table of Contents
1. [Overview & Features](#-overview--features)
2. [Prerequisites & Build Setup](#-prerequisites--build-setup)
   - [1. Configuring local.properties (GitHub Package Registry)](#1-configuring-localproperties-github-package-registry)
   - [2. Keystore Configuration (keystore.jks)](#2-keystore-configuration-keystorejks)
3. [App Drawer Visibility (DEFAULT vs LAUNCHER) & SecSettings Integration](#-app-drawer-visibility-default-vs-launcher--secsettings-integration)
4. [OTA Manifest & Updates Configuration](#-ota-manifest--updates-configuration)
   - [1. Changing the Manifest Raw GitHub Link](#1-changing-the-manifest-raw-github-link)
   - [2. Expected manifest.json Format](#2-expected-manifestjson-format)
   - [3. Download Hosting Requirements](#3-download-hosting-requirements)
   - [4. Changelog Configuration (changelog.txt)](#4-changelog-configuration-changelogtxt)
5. [Additional Technical Details & System Requirements](#-additional-technical-details--system-requirements)
   - [Required System Properties (build.prop)](#required-system-properties-buildprop)
   - [Recovery Flashing Mechanism](#recovery-flashing-mechanism)
6. [ROM System Integration, Privapp Permissions & Services Patch](#-rom-system-integration-privapp-permissions--services-patch)
   - [1. System App Placement](#1-system-app-placement)
   - [2. Adding ROM Privileged Permissions Files](#2-adding-rom-privileged-permissions-files)
   - [3. Samsung Priv-App Whitelist Bypass Patch (services.jar)](#3-samsung-priv-app-whitelist-bypass-patch-servicesjar)
7. [Compiling with Android Studio](#-compiling-with-android-studio)
8. [Credits & Acknowledgments](#-credits--acknowledgments)

---

## 🚀 Overview & Features

- **Samsung OneUI Design**: Crafted with SESL 8 and `oneui-design` libraries for a native Samsung OneUI 8 aesthetic.
- **Automated Background Checks**: Periodically checks for new ROM updates via `UpdatesCheckReceiver` and Android `AlarmManager`.
- **Background Downloads**: Resumable multi-threaded downloading service using OkHttp and foreground notifications (`DownloadForegroundService`).
- **Integrity Verification**: Automatic MD5 checksum verification (`Md5Verifier`) prior to flashing.
- **Recovery Auto-Flashing**: Generates OpenRecoveryScript commands (`/cache/recovery/openrefine`, `/cache/recovery/extended_command`, or `/cache/recovery/command`) and reboots to recovery (`RomInstall.kt`).

---

## 🛠️ Prerequisites & Build Setup

### System Requirements
- **Android Studio**: Ladybug, Jellyfish, or newer.
- **JDK**: Java 11 (Kotlin target JVM is 11).
- **Android SDK**: `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.

---

### 1. Configuring `local.properties` (GitHub Package Registry)

The project relies on external SESL 8 and OneUI libraries hosted on GitHub Packages under the `tribalfs` repository. To fetch these dependencies, you must configure your GitHub Package Registry (GPR) credentials.

Create or open the `local.properties` file in the root of the project (`Updater/local.properties`) and add your GitHub username and Personal Access Token (PAT):

```properties
sdk.dir=/path/to/android/sdk

# GitHub Package Registry credentials
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

> **Note**: Your GitHub Personal Access Token (`gpr.key`) must have at least the `read:packages` scope enabled.
> 
> Alternatively, you can configure these as environment variables on your system or CI runner:
> - `GPR_USER`
> - `GPR_KEY`

---

### 2. Keystore Configuration (`keystore.jks`)

If you have a custom signing keystore:
1. Place your `keystore.jks` file inside the `app/` directory (path: `app/keystore.jks`).
2. Follow the steps in the [Compiling with Android Studio](#-compiling-with-android-studio) section to sign the APK using Android Studio's Signed APK wizard.

> **Important**: For system-level operations (rebooting to recovery, installing packages, cache filesystem access), the app must either be signed with the platform signing key of your ROM or installed as a privileged system app in `/system/priv-app/`.

---

## 👁️ App Drawer Visibility (DEFAULT vs LAUNCHER) & SecSettings Integration

You can configure whether the Updater app appears as an icon in the application drawer (launcher) or remains hidden from the drawer (launched only via System Settings or specific intents).

File location: `app/src/main/AndroidManifest.xml`

Inspect the `<activity>` tag for `.MainActivity`:

### Option A: Show icon in App Drawer (Launcher)
```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

### Option B: Hide icon from App Drawer (DEFAULT)
Change `android.intent.category.LAUNCHER` to `android.intent.category.DEFAULT`:
```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### 🔗 SecSettings Integration (Samsung Settings Patch)
When hiding the application icon from the launcher drawer using `DEFAULT`, if you want the Updater entry to appear natively inside Samsung's Settings app (`SecSettings.apk`), you must apply the patch provided in the project repository at:

```
patch/0001-Introduce-Lich-Updater.patch
```
*(Full path: `/home/mrdemonc/Documents/developer/LichApps/patch/0001-Introduce-Lich-Updater.patch`)*

Apply this patch when decompiling/building `SecSettings.apk` to add the entry for Lich Updater into the Software Update settings menu.

---

## 🌐 OTA Manifest & Updates Configuration

### 1. Changing the Manifest Raw GitHub Link

The application fetches update metadata from a remote JSON manifest file hosted on GitHub or any raw web URL.

File location: `app/src/main/java/com/lich/updater/GetManifest.kt`

Modify the `MANIFEST_URL` string in the `companion object`:

```kotlin
companion object {
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPOSITORY/refs/heads/YOUR_BRANCH/manifest.json"
}
```

---

### 2. Expected `manifest.json` Format

The `manifest.json` file hosted at `MANIFEST_URL` must conform to the following JSON structure:

```json
{
  "response": [
    {
      "device": "a54x",
      "builddate": 20260528,
      "md5": "c9230270f98419433c0be38c46687c85",
      "patch": 20260405,
      "size": 7121828064,
      "bl": "XXUFEYI4",
      "url": "https://pixeldrain.com/api/file/[id]?download",
      "changelog": "https://raw.githubusercontent.com/MrDemonc/PROJECT-LichUI/refs/heads/Updates/changelog.txt",
      "version": "5.2.9"
    }
  ]
}
```

#### Field Reference:
| Field | Type | Description |
| :--- | :--- | :--- |
| `device` | String | Target device code name matching `ro.product.device` property (e.g. `"a54x"`). |
| `builddate` | Integer | Build release date formatted as `YYYYMMDD` (e.g. `20260528`). |
| `md5` | String | MD5 hash signature of the downloadable ROM zip file for integrity verification. |
| `patch` | Integer | Android security patch date formatted as `YYYYMMDD` (e.g. `20260405`). |
| `size` | Long | Total file size of the ROM zip package in bytes (e.g. `7121828064`). |
| `bl` | String | Recommended/required bootloader version string (e.g. `"XXUFEYI4"`). |
| `url` | String | Direct download link for the update ZIP package. |
| `changelog` | String | Direct raw link to the `changelog.txt` file for this update. |
| `version` | String | Version tag/release name of the update (e.g. `"5.2.9"`). |

---

### 3. Download Hosting Requirements

The `"url"` field inside `manifest.json` **must** provide a direct download link (a link that immediately streams the file upon request without requiring user interaction, HTML landing pages, or CAPTCHAs).

#### Recommended Cloud Hosting Services:
- **Free Hosting (e.g., [SourceForge](https://sourceforge.net/))**:
  - Completely free to host large ROM zip files.
  - Offers direct download mirrors, though download speeds may be slower.
- **High-Speed / Premium Hosting (e.g., [Pixeldrain](https://pixeldrain.com/))**:
  - Provides high-speed direct API download links (e.g., `https://pixeldrain.com/api/file/[id]?download`).
  - Fast download speeds for end-users, though it may require paid subscriptions or bandwidth plans for high traffic.

---

### 4. Changelog Configuration (`changelog.txt`)

- The changelog URL is configured dynamically per build in the `"changelog"` field inside `manifest.json`.
- `ChangelogReader.kt` fetches text content line-by-line from the specified URL and displays each line as a bullet point (`• item`) in the UI.
- Maintain your changelog file as a simple plain text file hosted on GitHub raw or any direct HTTP server.

---

## 🔍 Additional Technical Details & System Requirements

### Required System Properties (`build.prop`)
The application reads device and system information via `getprop` in `BuildPropReader.kt`:

- `ro.product.device`: Matches the `"device"` property in `manifest.json`.
- `ro.build.version.oneui`: OneUI version code (e.g. `80000` mapped to `8.0`).
- `ro.on.core.version`: Installed ROM version.
- `ro.on.core.build`: Installed build date string in `YYYYMMDD` format (compared against `builddate` in manifest to determine update availability).
- `ro.vendor.build.version.incremental`: Vendor build incremental string.

### Recovery Flashing Mechanism
When an update is downloaded and passes MD5 verification:
1. `GenerateScript.kt` writes OpenRecoveryScript commands to `/cache/recovery/openrefine`, `/cache/recovery/extended_command`, or `/cache/recovery/command`.
2. `RomInstall.kt` invokes `PowerManager.reboot("recovery")` or shell fallback (`reboot recovery` / `su -c 'reboot recovery'`) to reboot into custom recovery (TWRP/OrangeFox/etc.) and execute the flash commands automatically.

---

## 📱 ROM System Integration, Privapp Permissions & Services Patch

### 1. System App Placement

To include the Updater inside a custom ROM build:
1. Compile and sign the APK.
2. Copy the APK into your ROM's system tree at:
   `/system/priv-app/Updater/Updater.apk`
3. Ensure appropriate permissions: `0644` for `Updater.apk` and `0755` for `/system/priv-app/Updater/`.

---

### 2. Adding ROM Privileged Permissions Files

To grant the necessary privileged permissions (such as `REBOOT`, `RECOVERY`, `INSTALL_PACKAGES`, `ACCESS_CACHE_FILESYSTEM`) without package permission errors, you must copy the permission configuration files included in the project's `system/` directory (`/home/mrdemonc/Documents/developer/LichApps/system`) into your ROM:

Copy the following files into your ROM filesystem:
- `system/etc/permissions/privapp-permissions-com.lich.updater.xml` -> `/system/etc/permissions/privapp-permissions-com.lich.updater.xml`
- `system/etc/default-permissions/default-permissions-com.lich.updater.xml` -> `/system/etc/default-permissions/default-permissions-com.lich.updater.xml`

---

### 3. Samsung Priv-App Whitelist Bypass Patch (`services.jar`)

On Samsung OneUI ROMs (tested on **Samsung OneUI 8.5**), non-whitelisted privileged applications placed in `/system/priv-app/` may trigger package verification failures or boot loops due to Knox / PMRune whitelist enforcement.

Follow this guide to patch `services.jar` and allow non-whitelisted apps in `priv-app`:

#### Instructions:

1. **Decompile `services.jar`** using a smali tool (e.g., `apktool` or `baksmali`).

2. **Locate the file**:
   `smali_classes2/com/android/server/pm/InstallPackageHelper.smali`

3. **Search for the method**:
   `processParseResult(Lcom/android/server/pm/ParallelPackageParser$ParseResult;)V`

4. **Find this line**:
   ```smali
   sget-boolean v2, Lcom/samsung/android/rune/PMRune;->PM_ALLOWED_SYSTEM_PRELOAD_APPS:Z
   ```

5. **Replace it with**:
   ```smali
   goto :cond_0
   ```

6. **Recompile `services.jar`** and replace it in your ROM under `/system/framework/services.jar`.

---

## ⚙️ Compiling with Android Studio

1. Open **Android Studio**.
2. Click **Open** and select the `Updater` project folder.
3. Allow Gradle to perform the initial sync (ensure `local.properties` contains valid `gpr.user` and `gpr.key`).
4. To build and sign the APK:
   - Navigate to **Build > Generate Signed Bundle / APK...**
   - Select **APK** and click **Next**.
   - Browse and select your `keystore.jks` file (located in `app/keystore.jks` or your custom keystore path).
   - Enter your **Key store password**, **Key alias**, and **Key password**.
   - Click **Next**, select the **release** build variant, and click **Create** (or **Finish**).
5. The compiled and signed APK will be saved in your specified release output directory (e.g., `app/release/Updater.apk`).

---

## 🙏 Credits & Acknowledgments

This application was inspired by GitHub user [@salvogiangri](https://github.com/salvogiangri) and his work on:
- [OnUpdate (Mesa-Labs-Archive)](https://github.com/Mesa-Labs-Archive/OnUpdate.git) - Archived OTA Update application.
- [UN1CA ROM](https://github.com/salvogiangri/UN1CA.git) - Custom OneUI ROM project.
