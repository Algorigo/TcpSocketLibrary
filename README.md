# TcpSocketLibrary

TCP 클라이언트 소켓 하나를 RxJava3로 감싼 Android 라이브러리입니다. 기기와 바이트 단위로 요청/응답을 주고받는 용도로 만들어졌습니다.

1. 요청 바이트를 보냅니다.
2. 직접 작성한 **검증 함수(verifier)** 가 "응답이 다 왔다"고 판단하면, 그때까지 받은 바이트를 `Single` / `Observable`로 돌려받습니다.
3. 서버가 먼저 보내는 데이터는 수신 콜백으로 받을 수 있습니다.

- 배포 아티팩트: `com.algorigo.rx:tcpsocketlibrary` (버전은 `tcpsocketlibrary/build.gradle`의 `versionStr`, 현재 `1.3.0`)
- 언어/스택: Kotlin, RxJava3, RxRelay3
- 최소 Android 버전: 5.0 (API 21)
- `INTERNET` 권한은 라이브러리 manifest에 들어 있어 앱에 자동으로 병합됩니다.

## 빌드

### 요구 사항

- JDK 11
- Android SDK 31

### Gradle 속성

`~/.gradle/gradle.properties`에 설정하세요. 저장소에 커밋하지 마세요.

```properties
# 배포 저장소. 없으면 일반 빌드도 Gradle 설정 단계에서 실패합니다.
NEXUS_SNAPSHOT_REPOSITORY_URL=...   # master가 아닌 브랜치
NEXUS_REPOSITORY_URL=...            # master 브랜치

# publish 할 때만 필요
nexusUsername=...
nexusPassword=...
# GPG 서명용 signing.* 속성 (Gradle signing 플러그인)
```

### 명령어

```bash
./gradlew build                            # CI와 동일
./gradlew :app:installDebug                # 샘플 앱
./gradlew :tcpsocketlibrary:lintDebug
./gradlew :tcpsocketlibrary:testDebugUnitTest
```

테스트는 템플릿 예제뿐입니다.

## 의존성 추가

```groovy
repositories {
    maven { url NEXUS_REPOSITORY_URL }   // 릴리스 버전을 배포하는 저장소
}

dependencies {
    implementation 'com.algorigo.rx:tcpsocketlibrary:1.3.0'

    // 배포된 POM에 의존성이 들어 있지 않으므로 직접 추가해야 합니다.
    // rxrelay가 없으면 실행 중 NoClassDefFoundError가 납니다.
    implementation 'io.reactivex.rxjava3:rxkotlin:3.0.1'
    implementation 'io.reactivex.rxjava3:rxandroid:3.0.0'
    implementation 'com.jakewharton.rxrelay3:rxrelay:3.0.0'
}
```

## 사용 방법

### 연결하고 요청 보내기

```kotlin
val communication = TcpSocketCommunication("192.168.0.10", 5000)

// 응답이 완전히 도착했는지 판단하는 함수.
// 마지막으로 응답을 처리한 뒤 받은 바이트 전체가 들어옵니다.
// 예: 앞 4바이트(little endian)가 전체 길이
val verifier = { bytes: ByteArray ->
    bytes.size >= 4 &&
        ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int == bytes.size
}

disposable = communication.connectObservable(timeout = 5000)   // 응답 타임아웃(ms), 기본 30000
    .flatMapSingle { connection -> connection.sendDataSingle(request, verifier) }
    .observeOn(AndroidSchedulers.mainThread())                 // 결과는 소켓 스레드에서 옵니다
    .subscribe({ response -> /* ... */ }, { e -> /* TimeoutException, DisconnectedException ... */ })

disposable.dispose()   // 마지막 구독이 해제되면 소켓을 닫습니다
```

| 메서드 | 동작 |
|---|---|
| `sendDataSingle(bytes, verifier)` | 한 번 보내고, verifier가 `true`를 반환한 응답 하나를 받습니다. |
| `sendDataObservable(bytes, verifier)` | 응답을 받을 때마다 **같은 요청을 다시 보냅니다**(폴링). `dispose`할 때까지 계속됩니다. |
| `receiveDataObservable { connection, bytes -> Single<Boolean>? }` | 보낼 요청이 없을 때 들어온 데이터를 받습니다. `null`을 반환하면 계속 모으고, `Single`을 반환하면 그 데이터가 스트림으로 나갑니다. 연결당 한 번만 등록할 수 있습니다. |

장치별 프로토콜은 `TcpSocketCommunication`을 상속해서 만들면 됩니다. 샘플의 `MatrixSensor48Communication`을 참고하세요.

### 주의

- 내부 루프가 **500ms 간격으로 폴링**합니다. 요청 하나에 최소 두 번의 폴링이 걸리므로, `sendDataObservable`은 대략 1초에 한 번 정도 응답합니다.
- verifier가 **빈 배열**에 `true`를 반환하면 응답을 기다리지 않는 "보내고 끝" 요청이 됩니다. 이때 `Single` / `Observable`은 아무 값도, 종료 신호도 보내지 않습니다.
- 요청 큐가 LIFO입니다. 응답을 기다리는 중에 다른 요청을 구독하면 순서가 꼬입니다. 요청은 `concatMap` 등으로 **한 번에 하나씩** 보내세요.
- 결과·에러는 소켓 전용 스레드에서 전달됩니다. UI에 쓰려면 `observeOn`이 필요하고, 그 스레드를 막으면 통신이 멈춥니다.
- `TcpSocketCommunication.sendDataSingle()` 등은 연결 전에 호출하면 즉시 NPE가 납니다. `connectObservable()`이 내보낸 `TcpSocketConnection`을 사용하세요.
- 에러가 난 뒤 다시 연결하려면 `connectObservable()`을 **다시 호출**해야 합니다.
- 받은 데이터를 처리하지 않고 100,000바이트가 쌓이면 연결이 끊어집니다.
- 서버가 연결을 끊어도 다음 전송이 실패할 때까지 알아채지 못할 수 있습니다.

## 샘플 앱

`FirstActivity`에서 세 가지 데모를 실행합니다.

| 화면 | 내용 |
|---|---|
| Main | IP/포트를 입력해 MatrixSensor48 기기에 접속하고, 48×24 센서 값을 반복 요청해 그립니다. |
| Send | 앱 안의 `SendService`(포트 9999)에 입력한 문자열을 보냅니다. |
| Receive | 앱 안의 `ReceiveService`(포트 9998)가 5초마다 보내는 바이트를 받고, 반대 값을 응답합니다. |

## 프로젝트 구조

```
tcpsocketlibrary/   배포 라이브러리: TcpSocketCommunication(연결 관리), TcpSocketConnection(송수신 루프)
app/                샘플 앱 (com.algorigo.tcpsocketlibraryapp)
```

내부 동작은 [CLAUDE.md](CLAUDE.md)에 자세히 정리되어 있습니다.

## 릴리즈

git-flow 브랜치 전략(`master`, `develop`, `feature/*`, `release/vX.Y.Z`)을 사용합니다.

1. `tcpsocketlibrary/build.gradle`의 `versionStr`을 올립니다. 커밋 메시지 예: `version name 수정`
2. `master`에 머지하면 CircleCI가 `./gradlew build` 후 `./gradlew publish`를 실행합니다.
   - `master` 브랜치에서는 `versionStr` 그대로 배포됩니다.
   - 그 외 브랜치에서는 `<versionStr>-<사용자명>-SNAPSHOT`으로 배포됩니다.
3. 로컬에서 배포할 때는 `publish`가 AAR을 빌드하지 않으므로 `./gradlew :tcpsocketlibrary:assembleRelease`를 먼저 실행합니다.
