# Hướng dẫn cài đặt & chạy Server (cho người mới)

Tài liệu này hướng dẫn **từng bước** để chạy **server game + website** PVTK, kể
cả khi bạn chưa từng làm việc với Java. Cứ làm lần lượt từ trên xuống.

> Tóm tắt: cài JDK 21 → tải mã nguồn → chạy `./gradlew :web:run` → mở
> `http://localhost:8080`. Xong.

---

## 1. Chuẩn bị máy (cài JDK 21)

Server viết bằng **Java 21**. Bạn cần cài **JDK 21** (không phải JRE).

- **Windows / macOS / Linux**: tải “**Temurin JDK 21**” tại
  <https://adoptium.net/temurin/releases/?version=21> và cài như phần mềm thường.
- Kiểm tra sau khi cài, mở Terminal (macOS/Linux) hoặc PowerShell/CMD (Windows):

```bash
java -version
```

Phải hiện dòng có `21` (ví dụ `openjdk version "21..."`). Nếu báo lỗi “không tìm
thấy lệnh”, hãy cài lại JDK và mở lại Terminal.

> Không cần cài Gradle riêng — dự án đã kèm sẵn `gradlew` (Gradle Wrapper).

---

## 2. Tải mã nguồn

Nếu có **git**:

```bash
git clone https://github.com/thanhtinz/pvtk.git
cd pvtk
```

Không có git? Vào trang GitHub của dự án → nút **Code** → **Download ZIP** → giải
nén → mở Terminal trỏ vào thư mục vừa giải nén.

---

## 3. Chạy server + website (1 lệnh)

Tại thư mục dự án:

- **macOS / Linux:**
  ```bash
  ./gradlew :web:run
  ```
- **Windows (PowerShell/CMD):**
  ```bat
  gradlew.bat :web:run
  ```

Lần đầu chạy sẽ **tải thư viện** (Netty, Jackson…) nên hơi lâu, cần **mạng**.
Khi thấy dòng log:

```
PVTK game server listening on 0.0.0.0:30000
PVTK website listening on http://localhost:8080  (admin: admin/admin123)
```

là đã chạy thành công 🎉

- **Game server**: cổng `30000` (client kết nối vào đây).
- **Website**: <http://localhost:8080>
- **Trang admin**: <http://localhost:8080/admin.html>
  - Tài khoản admin mặc định: **`admin` / `admin123`** → **đổi mật khẩu ngay**
    (đăng nhập web → trang admin, hoặc xem mục 6).

Dừng server: bấm **Ctrl + C** trong Terminal.

> Chỉ muốn chạy **mỗi game** (không website): `./gradlew :server:run`.

---

## 4. Đổi cổng (nếu bị trùng)

Dùng biến môi trường trước khi chạy:

- macOS/Linux:
  ```bash
  PVTK_PORT=30000 PVTK_WEB_PORT=8080 ./gradlew :web:run
  ```
- Windows (PowerShell):
  ```powershell
  $env:PVTK_PORT=30000; $env:PVTK_WEB_PORT=8080; .\gradlew.bat :web:run
  ```

| Biến | Ý nghĩa | Mặc định |
|------|---------|----------|
| `PVTK_PORT` | cổng game server | `30000` |
| `PVTK_WEB_PORT` | cổng website | `8080` |
| `PVTK_HOST` | địa chỉ bind | `0.0.0.0` |
| `PVTK_ASSETS` | thư mục assets | tự dò `assets/` |

---

## 5. Dữ liệu được lưu ở đâu?

Tất cả lưu dạng file JSON trong thư mục **`data/`** (tự tạo khi chạy):

- `data/accounts.json` — tài khoản, mật khẩu (đã băm), vàng, số dư, cấp.
- `data/web.json` — tin tức/sự kiện, giftcode, sản phẩm webshop.

Sao lưu = copy thư mục `data/`. Muốn reset toàn bộ = xóa `data/`.

---

## 6. Việc nên làm ngay sau khi cài

1. Mở <http://localhost:8080> → **Đăng nhập** bằng `admin` / `admin123`.
2. Vào **Admin** (góc phải) → tạo **giftcode**, **tin tức**, **sản phẩm webshop**.
3. **Đổi mật khẩu admin**: trang chủ → **Cá nhân** → *Đổi mật khẩu*.
4. Thử **Gửi vật phẩm** (tab *Gửi vật phẩm*): tìm item, chọn nhiều, gửi kèm thư.

---

## 6b. Cấu hình nạp tiền qua SePay (chuyển khoản ngân hàng)

Luồng: người chơi nạp **Xu** trên web qua chuyển khoản → trong game gõ lệnh
`convert <số xu>` để đổi **Xu → Tiền nạp (coin)** dùng trong game.

1. Tạo tài khoản tại <https://sepay.vn>, liên kết ngân hàng của bạn.
2. Vào **Admin → Cổng nạp (SePay)** trên web của bạn, điền:
   - **Bật cổng nạp**, **Mã ngân hàng** (vd `MBBank`), **Số tài khoản**,
     **Chủ tài khoản**, **Prefix** nội dung CK (vd `PVTK`), và **Webhook API Key**
     (một chuỗi bí mật bạn tự đặt).
3. Vào **Admin → Gói nạp** tạo các gói (vd: 50.000đ → 500 Xu +75 thưởng).
4. Trong bảng điều khiển **SePay**, thêm **Webhook**:
   - URL: `http://<tên-miền-hoặc-IP>:8080/api/sepay/webhook`
   - Kiểu xác thực: **API Key** → nhập đúng chuỗi đã đặt ở bước 2 (SePay sẽ gửi
     header `Authorization: Apikey <key>`).
5. Xong! Người chơi vào **Nạp thẻ** → chọn gói → quét QR / CK đúng nội dung →
   SePay gọi webhook → hệ thống tự cộng Xu. Xem đơn ở **Admin → Đơn nạp**.

> Mẹo test nhanh không cần ngân hàng: gọi thử webhook bằng `curl` với đúng
> `Apikey` và `content` = mã đơn (xem `README`/`docs` hoặc tab Đơn nạp).

---

## 7. Cho người chơi kết nối từ máy khác (LAN/Internet)

- Người chơi nhập **IP máy chủ của bạn** + cổng `30000` trong client
  (`--host <IP> --port 30000`).
- Mở/Forward **cổng 30000 (game)** và **8080 (web)** trên router/tường lửa.
- Tìm IP nội bộ: Windows `ipconfig`, macOS/Linux `ip addr` hoặc `ifconfig`.

---

## 8. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|------------|-----------|
| `java: command not found` | Chưa cài JDK 21, hoặc chưa mở lại Terminal sau khi cài. |
| Build đứng/lâu ở lần đầu | Bình thường (đang tải thư viện). Cần mạng; chờ tới khi xong. |
| `Address already in use` | Cổng bị chiếm → đổi `PVTK_PORT`/`PVTK_WEB_PORT` (mục 4). |
| `Permission denied: ./gradlew` | Chạy `chmod +x gradlew` (macOS/Linux). |
| Web mở được nhưng login admin sai | Xóa `data/accounts.json` để tạo lại admin mặc định. |
| Server log “Assets directory not found” | Chạy lệnh **tại thư mục gốc dự án** (nơi có thư mục `assets/`). |

---

## 9. Chạy production (tùy chọn)

Đóng gói server thành thư mục chạy độc lập:

```bash
./gradlew :web:installDist
# Kết quả ở: web/build/install/web/bin/web   (web.bat trên Windows)
```

Chạy nền trên Linux (ví dụ với `nohup`):

```bash
cd web/build/install/web
nohup ./bin/web > pvtk.log 2>&1 &
```

Khuyến nghị thật sự khi lên mạng: đặt sau **reverse proxy (nginx)** có HTTPS, đổi
mật khẩu admin, và sao lưu `data/` định kỳ.
