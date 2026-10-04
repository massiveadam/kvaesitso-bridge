# Releases and Obtainium

The update source is https://github.com/massiveadam/kvaesitso-bridge. Stable GitHub Releases contain one universal, signed `kvaesitso-bridge-VERSION.apk` and `SHA256SUMS`. Obtainium follows the repository's release API with its default GitHub source settings. No APK filter is needed.

On the phone, open Obtainium, choose **Add app**, paste the repository URL, then add and install. The README also has a one-tap link using Obtainium's [documented deep-link format](https://wiki.obtainium.imranr.dev/deep_links/). Obtainium checks for later releases and handles the Android update installation prompt. Bridge does not need its own updater or network permission.

If a debug build of Bridge is already installed, uninstall it once before installing the release. The release uses a dedicated signing certificate. This clears local settings, so grant Notification Access and add the widget again. Subsequent releases use the same certificate and preserve app data during updates.

## Publish an update

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`. Keep versionCode increasing.
2. Commit and push to `main`. Wait for the Android workflow to pass.
3. Create and push `vVERSION` matching versionName, for example `git tag v0.1.1` followed by `git push origin v0.1.1`.
4. The tag workflow builds, tests and lints, signs the verified release APK, checks package/version/certificate/alignment, and publishes it as a GitHub Release. Obtainium discovers that release.

The signing key is held in GitHub Actions secrets `APK_SIGNING_KEYSTORE_BASE64` and `APK_SIGNING_PASSWORD`. Only the tag publishing job reads them. Pull request checks do not use signing secrets. No private key or password is stored in Git.

Back up the original keystore and password in a private, durable location. The initial local copy is outside the checkout under `~/.local/share/kvaesitso-bridge/signing/`, with directory mode 700 and file mode 600. This is a local copy, not a disaster-recovery backup. Never generate a replacement key for an ordinary update. Android requires compatible signing certificates for in-place updates; see [Android's signing documentation](https://developer.android.com/studio/publish/app-signing).

The public certificate and its SHA-256 fingerprint are in `release-signing-certificate.pem` and `release-signing-certificate.sha256`. The signing helper rejects APKs with another certificate. A local release can use `tools/sign_release.py` with the same key supplied through `APK_SIGNING_KEYSTORE` and `APK_SIGNING_PASSWORD`.
