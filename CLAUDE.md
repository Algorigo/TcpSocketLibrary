# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android library (Kotlin, RxJava3, RxRelay3) that wraps one raw TCP client socket in an Rx request/response API. You send bytes, a caller-supplied *verifier* decides when the accumulated reply is complete, and the reply comes back as a `Single` or `Observable`. There is also a callback hook for unsolicited server pushes. It is published as `com.algorigo.rx:tcpsocketlibrary` via `maven-publish` + `signing`. The version is `versionStr` in `tcpsocketlibrary/build.gradle` (currently 1.3.0). `app` (`com.algorigo.tcpsocketlibraryapp`) is a sample with three demos.

## Build & test

```bash
./gradlew build                                         # what CI runs
./gradlew :app:installDebug
./gradlew :tcpsocketlibrary:lintDebug
./gradlew :tcpsocketlibrary:testDebugUnitTest --tests "com.algorigo.tcpsocketlibrary.ExampleUnitTest"
./gradlew :tcpsocketlibrary:assembleRelease             # must run before publish (see below)
./gradlew :tcpsocketlibrary:publish
```

Only template example tests exist, written in Java. Toolchain: AGP 7.0.4, Gradle 7.4, Kotlin 1.6.0, Java/JVM target 11, compileSdk/targetSdk 31, minSdk 21. Javadoc tasks are disabled for all projects. The sample app still uses `kotlin-android-extensions` synthetics (`kotlinx.android.synthetic.*`), which Kotlin 1.8 removed. Migrate the sample to ViewBinding before bumping Kotlin that far.

**Required Gradle properties** (in `~/.gradle/gradle.properties`; CI downloads it from `$GRADLE_PROPERTY_PATH`):
- `NEXUS_SNAPSHOT_REPOSITORY_URL` on any branch except `master`, `NEXUS_REPOSITORY_URL` on `master`. The `publishing` block reads it at configuration time, so every build fails without it.
- `nexusUsername`, `nexusPassword`, and the signing plugin's `signing.*` properties are needed only for `publish`. CI writes the keyring to `tcpsocketlibrary/secret-keys.gpg`, which is **not** gitignored.

**Publishing quirks:**
- `version` is `versionStr` only when the branch is exactly `master`. Otherwise it is `<versionStr>-<user.name>-SNAPSHOT`.
- The publication attaches `build/outputs/aar/tcpsocketlibrary-release.aar` by path, so `publish` does not build it and the POM has **no dependencies**. Consumers must add RxJava3 *and* `com.jakewharton.rxrelay3:rxrelay` (used internally by `TcpSocketConnection`), or they hit `NoClassDefFoundError`.
- The library manifest adds `android.permission.INTERNET` to consumers.
- CI publishes from `master` and `release/*`.

## Architecture

Two classes, both in `tcpsocketlibrary/src/main/java/com/algorigo/tcpsocketlibrary/`.

**`TcpSocketCommunication(ip, port)`** is `open`: subclass it per device protocol, as the sample's `MatrixSensor48Communication` does. It is a ref-counted holder for one connection.
- `connectObservable(timeout = 30000, printLog = false)`: the first subscriber opens the socket on `Schedulers.io()`, later subscribers share it through a `BehaviorSubject`, and the last unsubscribe closes it.
- On error the subject is replaced. An `Observable` returned *earlier* still points at the old, errored subject, so call `connectObservable()` again to reconnect.
- The subscriber counter is a plain `Int`, not atomic.
- Its `sendData*` / `receiveDataObservable` helpers dereference `connection!!` at **call time**. Calling them before the connection exists throws an NPE synchronously, not as an Rx error. Prefer calling the methods on the emitted `TcpSocketConnection`.

**`TcpSocketConnection`** connects in its constructor, synchronously. `Socket(addr, port)` uses the OS default connect timeout, because `timeout` is applied only as `soTimeout` and as the per-request response timeout. The constructor then starts a plain `Thread` running `messageHandleInner`, a **500 ms polling loop**. Each tick:
1. Append `inputStream.available()` bytes to a fixed 100,000-byte buffer. Overflow throws, which ends the loop and disconnects.
2. If the send queue is non-empty, look at its head:
   - **Not sent yet:** write it and timestamp it. If `verifier(byteArrayOf())` returns `true`, drop it as fire-and-forget. Its `Single`/`Observable` then **never emits or terminates**.
   - **Sent, past `timeout`:** clear the buffer and fail it with `TimeoutException`.
   - **Sent, `verifier(wholeBuffer)` returns `true`:** clear the buffer and emit. A `Single` calls `onSuccess`. An `Observable` calls `onNext` and re-enqueues an unsent copy, so the same request is re-sent next tick. `sendDataObservable` is therefore a polling stream until disposed.
   - **The verifier throws:** that exception becomes the error.
3. If the queue is empty and a receive function is registered, call it with the whole buffer, which may be empty.
   - Returning `null` keeps accumulating.
   - Returning a `Single` consumes the data: when that `Single` is subscribed, the buffer is cleared and the bytes go out on `receiveDataObservable`'s relay. The `Single`'s value is ignored. The function may use the passed connection to send a reply.
4. `Thread.sleep(500)`.

Consequences to keep in mind:
- **The verifier is the framing layer.** It sees everything received since the last clear, including stray bytes, and must return `true` only for a complete response. The sample checks that a 4-byte little-endian length prefix equals the total size.
- Each request/response takes at least two ticks, so a `sendDataObservable` poll yields at most about one response per second.
- Unsolicited data reaches the receive function only while no request is queued. A pending request's verifier sees everything.
- `receiveDataObservable` can be registered once per connection. A second call errors with `IllegalStateException`, and the function is never cleared.
- **The queue is LIFO.** It is a `java.util.ArrayDeque` used with `push`/`peek`. A request subscribed while another is in flight jumps ahead, and its verifier then sees the older reply. Issue requests one at a time, for example with `concatMap`.
- `sendDataQueue` is mutated from `Schedulers.io()` threads (`create`/`doFinally`) and from the loop thread without synchronization.
- Results, errors, and relay items are emitted **on the loop thread**. Use `observeOn` for UI, and never block there, because that stalls the socket.
- The loop never does a blocking `read()`. `available()` does not report a peer close, so a server-side disconnect usually surfaces only when a write throws. When the loop exits, `connectObservable` errors with `DisconnectedException` (if still subscribed). `close()` fails queued requests with `DisconnectedException`.

## Sample app

`FirstActivity` launches three demos:
- `MainActivity` talks to a "MatrixSensor48" device by IP and port. The request is 10 bytes: `[length=10 LE int][cmd 0x0031 LE short][0 LE int]`. The response is a 4-byte LE length followed by 48×24 big-endian uint16 values starting at byte 8, scaled by `/65535` and drawn by `SensorView`.
- `SendActivity` + `SendService` use a local server on port 9999 that replies `[firstByte, 0xFF]`. The activity's `{ true }` verifier makes each send fire-and-forget, so its result callback never runs.
- `ReceiveActivity` + `ReceiveService` use a local server on port 9998 that pushes `0xFF`/`0x00` every 5 s. The receive function answers with the opposite byte.

`Rx2ServiceBindingFactory.kt` sits in the default (root) package.

## Conventions

- Git-flow: `master`, `develop`, `feature/*`, `release/vX.Y.Z`. Tags run `v1.0.1` to `v1.3.0`. A release bumps `versionStr` in `tcpsocketlibrary/build.gradle` with the commit message `version name 수정`. Commit messages are mostly Korean.
- The public API keeps the misspelling `receiveDataVarifier`. Renaming it breaks callers that use named arguments.
