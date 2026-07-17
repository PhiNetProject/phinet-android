# ΦNET — Android (com + browser + Vault), embedded node

A branded Android app for **ΦNET** that runs a **real on-device node**: the
phone bootstraps to the backbone itself, holds its own identity, pulls the
consensus, dials guards, and routes `.phinet` hidden services over a circuit.

<table align="center">
  <tr>
    <td><img src="https://github.com/user-attachments/assets/d684cf0c-328c-487b-80d2-cfc40d3ba82f" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/d3181801-ad76-4018-adf9-6a9197610fd0" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/b6b7044b-0b44-46ce-a525-7e7d96b6f43c" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/88de1620-d00d-428f-ae67-f8ced5689888" width="180"></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/50442e5c-86ef-47de-8ec9-ec21df26a244" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/1d31b780-1467-4248-9d14-309626252c78" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/fda703b9-5c33-41d0-af63-744c792ccc7e" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/36cd2915-15ed-42e2-9336-737942e219e1" width="180"></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/f5b5bd5d-716c-408f-bb98-3fbc991f34c7" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/279b5bb6-1ac9-4266-88cf-19dbf95fef06" width="180"></td>
    <td><img src="https://github.com/user-attachments/assets/d65bbc70-bd69-4d60-824d-8b9954049440" width="180"></td>
    <td></td>
  </tr>
</table>

### What the phone can and cannot be

It is a full **client node**: dials out, builds circuits, sends/receives com,
browses `.phinet`. It is **not an inbound relay** — carrier NAT means other
peers can't connect *to* the phone, so it won't carry others' circuits or
appear as a reachable relay. (Same as Tor Browser on mobile.) It still enrolls
in the consensus as a node, but as client-grade, not a routable relay.


## Vault (Lockr-based, encrypted)

The Vault is a port of **Lockr**'s encrypted store, using the same native
crypto (`liblockr.so`, built from `app/src/main/cpp` via CMake): **Argon2id**
derives a master key from your passphrase, and items are sealed with
**XChaCha20-Poly1305**. The vault is locked until you enter your passphrase;
the key stays in memory only for the session and never touches disk.

**Sharing replaces Lockr's Bluetooth/Nearby mesh with ΦNET.** The key
simplification: com already seals every message to the recipient's key
(sealed sender + blinded address), so there's no SPAKE2 pairing or transit-key
exchange — you pick a com contact and the item is delivered confidentially
over the overlay. The recipient sees it as a "Shared from Vault" card in chat.


## The native daemon (build it yourself)

The app runs the ΦNET node as `libphinetdaemon.so`, cross-compiled from the
Rust source in [`phinet`](../phinet). It is **not** committed here:

```bash
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/26.3.11579264   # r26+
./build-android.sh /path/to/phinet
```

That produces `app/src/main/jniLibs/<abi>/libphinetdaemon.so` for arm64-v8a,
armeabi-v7a and x86_64. You'll need a Rust toolchain with the Android targets installed.

`liblockr.so` is likewise not committed — CMake builds it from
`app/src/main/cpp/` during every Gradle build.

## Build

1. **Cross-compile the daemon** (one-time, needs Rust + Android NDK):
   ```
   git clone https://github.com/PhiNetProject/phinet
   
   cd phinet/
   
   cargo install cargo-ndk
   
   cargo build --release
   ```
   This drops `libphinetdaemon.so` into `jniLibs/arm64-v8a` and `x86_64`.
2. ** Get Android Source **
   ```
   git clone https://github.com/PhiNetProject/phinet-android
   
   cd phinet-android/
   
   rustup target add aarch64-linux-android x86_64-linux-android

   export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/26.3.11579264"

   ./build-android.sh /path/to/phinet

   ./gradlew :app:assembleDebug

   export PATH="$HOME/Android/Sdk/platform-tools:$PATH"

   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

On first run, grant the notification permission (so the node service can stay
foreground), and give it ~30–60s to bootstrap + pull the consensus.
