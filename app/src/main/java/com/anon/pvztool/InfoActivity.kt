package com.anon.pvztool

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val toolbar: MaterialToolbar = findViewById(R.id.infoToolbar)
        val body: TextView = findViewById(R.id.infoBody)
        toolbar.setNavigationOnClickListener { finish() }

        body.text = """
CÁCH DÙNG PvZ TOOL

BƯỚC 0 — Chuẩn bị
• Cài Shizuku (F-Droid hoặc Play Store)
• Mở Shizuku → Start service (qua Wireless debugging)
• Vào app này → nhấn "Cấp quyền Shizuku" → Cho phép
• Cấp "All files access" khi được hỏi

BƯỚC 1 — Quét giá trị để tìm offset
1. Mở PvZ2, ghi nhớ số Coin / Gems hiện tại (ví dụ Coin = 1220)
2. Mở PvZ Tool → nhấn "1. Quét giá trị"
3. Nhập 1220
4. Chọn kiểu: int32 LE (mặc định — hầu hết giá trị trong PvZ2 dùng kiểu này)
5. Nhấn "Quét"
6. Kết quả hiện danh sách offset khớp

Nếu quá nhiều kết quả (giá trị tròn như 1000, 5000):
• Tiêu 1 ít coin trong game → quay lại quét với số mới
• Kết quả sẽ lọc bớt — chỉ còn 1-3 offset

BƯỚC 2 — Lưu offset
• Tap vào offset bạn cho là đúng (thường là offset duy nhất sau khi lọc)
• Panel dưới hiện ra → chọn field (coin / gem / mint / gauntlet / pennyfuel / zensun / trophy)
• Nhấn "Lưu offset"

BƯỚC 3 — Chỉnh giá trị
1. Vào "2. Chỉnh giá trị"
2. Slider + ô nhập cho từng field đã lưu
3. Kéo slider hoặc nhập số mới
4. Nhấn "Áp dụng tất cả" → backup tự động → ghi vào pp.dat

BƯỚC 4 — Trong game
• Force Stop PvZ2 (Settings → Apps → PvZ2 → Force Stop)
• Mở lại → giá trị mới
• Nếu chưa đổi → khởi động lại điện thoại

QUẢN LÝ BACKUP
• Mỗi lần "Áp dụng" tự tạo backup vào /Download/pvz_backups/
• Vào "Quản lý backup" để khôi phục / xóa
• Backup đặt tên theo timestamp, dễ tìm

MẸO TÌM OFFSET NHANH
• Coin: giá trị lẻ (1220, 3421...) dễ tìm hơn giá trị tròn
• Gems: cũng vậy — tiêu vài gems để có số lẻ trước khi quét
• Nếu giá trị = 0 → không quét được (match quá nhiều) → tiêu/nhận trước
• int32 LE chiếm 4 byte: ví dụ 1220 = C4 04 00 00

LƯU Ý
• Tool này chỉ patch dữ liệu — không gửi file đi đâu
• Mọi thao tác đều chạy trên máy bạn
• Luôn có backup trước khi thử giá trị lớn
• Nếu game update → offset có thể đổi → quét lại từ Bước 1
        """.trimIndent()
    }
}
