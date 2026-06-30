# Hướng dẫn build Client (cho người mới)

Hướng dẫn **từng bước** để build và chạy client PVTK trên **PC, Java (console),
Android và iOS**. Cứ làm theo đúng thứ tự là chạy được.

> Tất cả client dùng chung mã nguồn trong thư mục `client/`:
> `client/core` (mạng) + `client/game` (giao diện libGDX) + các launcher
> `client/desktop`, `client/android`, `client/ios`, và `client/java` (console).

---

## 0. Chuẩn bị chung

1. Cài **JDK 21** (xem [SETUP_SERVER.md](SETUP_SERVER.md) mục 1).
2. Tải mã nguồn (`git clone …` hoặc tải ZIP), mở Terminal tại thư mục dự án.
3. **Chạy server trước** để client có chỗ kết nối:
   ```bash
   ./gradlew :web:run        # hoặc :server:run
   ```
   Mặc định game ở cổng `30000`.

Lệnh dùng `./gradlew` trên macOS/Linux, `gradlew.bat` trên Windows.

---

## 1. Client PC (đồ họa, libGDX desktop) — dễ nhất

Không cần cài thêm gì ngoài JDK.

```bash
./gradlew :client:desktop:run --args="--host 127.0.0.1 --port 30000 --user Alice"
```

- Cửa sổ game mở ra, tự kết nối và đăng nhập tên `Alice`.
- **Chạm/click ô** để di chuyển; **chạm vào quái** để vào trận đánh theo lượt.
- Mở thêm một cửa sổ nữa với `--user Bob` để thấy 2 người chơi cùng lúc.

Đóng gói thành ứng dụng chạy độc lập (không cần Gradle):

```bash
./gradlew :client:desktop:installDist
# Chạy: client/desktop/build/install/desktop/bin/desktop
```

---

## 2. Client Java (console, không cần màn hình)

Hữu ích để test nhanh hoặc chạy trên máy chủ không có GUI.

```bash
./gradlew :client:java:run --args="--host 127.0.0.1 --port 30000 --user Alice"
```

Gõ lệnh trong console: `m <x> <y>` (đi), `s <text>` (chat), `who`, `bag`,
`battle <id>`, `plan <enemyIndex>`, `shop`, `quests`, `arena`, `quit`… (gõ sai sẽ
hiện danh sách lệnh).

---

## 3. Client Android

### 3.1 Cài Android SDK
- Cách dễ nhất: cài **Android Studio** (<https://developer.android.com/studio>).
  Khi cài, nó tự tải **Android SDK**.
- Tạo file **`local.properties`** ở thư mục gốc dự án trỏ tới SDK:
  ```properties
  sdk.dir=/đường/dẫn/tới/Android/Sdk
  ```
  - Windows ví dụ: `sdk.dir=C\:\\Users\\Ban\\AppData\\Local\\Android\\Sdk`
  - macOS ví dụ: `sdk.dir=/Users/ban/Library/Android/sdk`

> Module Android chỉ tham gia build khi có `ANDROID_HOME` hoặc `local.properties`
> (xem `settings.gradle.kts`), nên trên máy không có SDK thì phần còn lại vẫn build.

### 3.2 Build APK
```bash
./gradlew :client:android:assembleDebug
```
File APK nằm ở: `client/android/build/outputs/apk/debug/`.

### 3.3 Cài & chạy
- Bật **USB debugging** trên điện thoại, cắm cáp, rồi:
  ```bash
  adb install -r client/android/build/outputs/apk/debug/*.apk
  ```
- Hoặc mở `client/android` bằng **Android Studio** → bấm **Run ▶**.
- Đổi địa chỉ server: truyền qua intent extras `pvtk_host`, `pvtk_port`,
  `pvtk_user` (hoặc sửa mặc định trong `AndroidLauncher.java`). Khi test trên máy
  ảo Android, IP của máy tính là **`10.0.2.2`** (không phải `127.0.0.1`).

---

## 4. Client iOS (chỉ trên macOS)

Cần **macOS + Xcode + RoboVM**.

1. Cài **Xcode** từ App Store.
2. Bật module iOS: mở `client/ios/build.gradle.kts`, thêm plugin RoboVM:
   ```kotlin
   plugins {
       java
       id("com.mobidevelop.robovm") version "2.3.21"
   }
   ```
3. Build/chạy (đặt biến `PVTK_BUILD_IOS=1` để bật module iOS trong settings):
   ```bash
   PVTK_BUILD_IOS=1 ./gradlew :client:ios:launchIPhoneSimulator   # chạy máy ảo
   PVTK_BUILD_IOS=1 ./gradlew :client:ios:createIPA               # xuất file .ipa
   ```

Cấu hình iOS (icon, frameworks, assets) nằm ở `client/ios/robovm.xml`.

---

## 5. Build tất cả module một lần

```bash
./gradlew build
```

Lệnh này biên dịch + chạy test cho mọi module **trừ** Android/iOS (vì cần SDK
riêng), nên luôn xanh trên máy chỉ có JDK.

---

## 6. (Tùy chọn) Hiện sprite thật từ asset gốc

Client đã tự nạp sprite đã giải mã từ `assets/common/`. Muốn tự xuất các khung
hình ra PNG để xem/kiểm tra:

```bash
./gradlew :tools:run --args="assets out/sprites"
# Kết quả: out/sprites/<thư mục>/<tên>/<frameId>.png
```

---

## 7. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|------------|-----------|
| Client mở nhưng “connect failed” | Server chưa chạy, hoặc sai `--host/--port`. Chạy server trước. |
| Android: `SDK location not found` | Tạo `local.properties` với `sdk.dir=…` (mục 3.1). |
| Máy ảo Android không kết nối được | Dùng host `10.0.2.2` thay cho `127.0.0.1`. |
| iOS task không xuất hiện | Phải trên macOS, đã thêm plugin RoboVM và đặt `PVTK_BUILD_IOS=1`. |
| Desktop báo lỗi OpenGL/headless | Máy cần có màn hình/GPU; trên server không GUI hãy dùng `:client:java`. |
| Build lần đầu rất lâu | Đang tải libGDX/Netty… cần mạng, chờ hoàn tất. |

---

Cần chi tiết kiến trúc & giao thức? Xem [ARCHITECTURE.md](ARCHITECTURE.md),
[PROTOCOL.md](PROTOCOL.md), [OPCODES.md](OPCODES.md).
