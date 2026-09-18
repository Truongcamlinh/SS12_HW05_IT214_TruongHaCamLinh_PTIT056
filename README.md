# SS12 HW05 - Circuit Breaker hay Retry Pattern?


## 1. Bối cảnh

Order Service gọi hệ thống GHTK để tạo vận đơn. GHTK thỉnh thoảng gặp lỗi mạng chớp nhoáng: một request bị timeout nhưng lời gọi ngay sau đó lại thành công. Đây là `Transient Failure`, tức lỗi tạm thời có khả năng tự hồi phục trong thời gian rất ngắn.

Hai phương án được xem xét là Circuit Breaker thuần túy và Retry Pattern có khoảng nghỉ giữa các lần gọi.

## 2. Giải pháp 1 - Circuit Breaker thuần túy

Circuit Breaker theo dõi tỷ lệ lỗi trong một cửa sổ thống kê. Khi tỷ lệ lỗi vượt ngưỡng, circuit chuyển sang `OPEN` và từ chối nhanh các request mới trong một khoảng thời gian.

Ưu điểm:

- Bảo vệ Order Service khỏi chờ đợi hàng loạt request khi GHTK sập hoàn toàn.
- Giảm lưu lượng gửi tới hệ thống đang gặp sự cố.
- Fallback nhanh khi circuit đã mở.

Nhược điểm trong bài toán này:

- Không tự gọi lại request vừa bị lỗi.
- Một timeout tạm thời vẫn làm đơn hiện tại thất bại dù lần gọi tiếp theo có thể thành công.
- Nếu cấu hình quá nhạy, các lỗi mạng rời rạc có thể khiến circuit mở không cần thiết.

Circuit Breaker phù hợp hơn với lỗi kéo dài hoặc hệ thống đích sập hẳn.

## 3. Giải pháp 2 - Retry Pattern

Retry Pattern gọi lại thao tác khi gặp lỗi được xác định là tạm thời. Khoảng nghỉ giúp tránh gọi dồn dập ngay khi đường truyền chưa ổn định.

Ưu điểm:

- Có khả năng tự khôi phục request bị timeout do network glitch.
- Người dùng ít gặp lỗi hơn khi sự cố chỉ tồn tại trong thời gian ngắn.
- Có thể giới hạn loại ngoại lệ được retry để không gọi lại lỗi dữ liệu hoặc lỗi nghiệp vụ.

Nhược điểm:

- Làm tăng thời gian phản hồi của request khi nhiều lần thử đều thất bại.
- Tăng số request tới GHTK.
- Nếu GHTK sập hoàn toàn, retry đơn thuần làm tăng tải lên hệ thống đang lỗi.
- Có nguy cơ lặp lại thao tác tạo vận đơn hoặc trừ tiền nếu API không có tính lũy đẳng.

## 4. Bảng so sánh

| Tiêu chí | Circuit Breaker thuần túy | Retry với khoảng nghỉ |
|---|---|---|
| Lỗi mạng chập chờn | Không cứu được request hiện tại | Phù hợp, lần gọi sau thường thành công |
| Hệ thống sập hoàn toàn | Tốt, mở circuit và từ chối nhanh | Không tốt nếu dùng một mình, tạo thêm tải |
| Thời gian phản hồi khi lỗi | Nhanh khi circuit đã mở | Chậm hơn do phải chờ giữa các lần gọi |
| Lưu lượng tới GHTK | Giảm mạnh khi circuit mở | Tăng theo số lần thử lại |
| Độ phức tạp | Cần chọn window, threshold và minimum calls | Cần chọn số lần gọi, khoảng nghỉ và loại lỗi |
| Rủi ro thao tác trùng | Thấp hơn vì không tự gọi lại | Cao nếu API không hỗ trợ idempotency |
| Trường hợp phù hợp | Lỗi kéo dài, service sập hoặc quá tải | Timeout tạm thời, lỗi mạng có thể tự hồi phục |

Trong hệ thống thực tế có thể kết hợp Retry ở bên trong và Circuit Breaker ở bên ngoài: retry xử lý lỗi chớp nhoáng, còn Circuit Breaker ngắt kết nối khi tất cả lần retry liên tục thất bại trên nhiều request.

## 5. Lựa chọn cho StoreX

Giải pháp được chọn là **Retry Pattern**, vì đặc điểm chính của GHTK là lỗi mạng tạm thời. Gọi lại có xác suất thành công cao và tránh làm người dùng phải tạo lại đơn thủ công.

Cấu hình:

```yaml
resilience4j:
  retry:
    instances:
      ghtkClient:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions:
          - java.util.concurrent.TimeoutException
        ignore-exceptions:
          - java.lang.IllegalArgumentException
```

Ý nghĩa:

- `max-attempts: 3`: tối đa 3 lần thực thi, bao gồm lần gọi đầu tiên và 2 lần gọi lại.
- `wait-duration: 2s`: nghỉ 2 giây trước mỗi lần thử tiếp theo.
- `retry-exceptions`: chỉ retry khi gặp `TimeoutException`.
- `ignore-exceptions`: không retry lỗi dữ liệu đầu vào.

Nếu nghiệp vụ hiểu “retry 3 lần” là 3 lần gọi lại ngoài lần đầu, cần đặt `max-attempts: 4`. Trong bài này sử dụng giá trị 3 theo cấu hình Resilience4j thường được yêu cầu trong đề.

## 6. Exponential Backoff

Cấu hình đề bài yêu cầu mỗi lần cách nhau đúng 2 giây nên phần triển khai dùng khoảng nghỉ cố định:

```text
Lần đầu -> chờ 2 giây -> lần 2 -> chờ 2 giây -> lần 3
```

Nếu GHTK yêu cầu exponential backoff thực sự, có thể bật hệ số nhân để khoảng nghỉ tăng dần:

```yaml
enable-exponential-backoff: true
exponential-backoff-multiplier: 2
```

Khi đó, với thời gian ban đầu 2 giây, các khoảng chờ sẽ là 2 giây, 4 giây, 8 giây. Cách này giảm tải tốt hơn khi hệ thống đích đang quá tải, nhưng không còn đúng yêu cầu “mỗi lần cách nhau 2 giây”.

## 7. Bẫy trừ tiền và Idempotency

Timeout không chứng minh request đã thất bại ở phía GHTK. Có thể GHTK đã tạo vận đơn hoặc trừ tiền thành công, nhưng response bị mất trên đường về. Nếu Order Service retry, GHTK có thể thực hiện thao tác lần thứ hai.

Hậu quả có thể gồm:

- Một đơn hàng tạo nhiều mã vận đơn.
- Tài khoản bị trừ phí vận chuyển nhiều lần.
- Dữ liệu giữa StoreX và GHTK không đồng nhất.

Giải pháp là **Idempotency**: nhiều request có cùng ý nghĩa nghiệp vụ chỉ tạo ra một kết quả duy nhất. Order Service gửi một khóa ổn định trong mọi lần retry:

```http
Idempotency-Key: ORD-1001
```

Trong code, `orderId` được dùng làm `Idempotency-Key`. GHTK cần lưu khóa này cùng kết quả lần xử lý đầu tiên. Khi nhận lại cùng khóa, GHTK trả kết quả cũ thay vì tạo vận đơn hoặc trừ tiền lần nữa.

Không được tạo UUID mới cho từng lần retry, vì như vậy phía GHTK sẽ xem chúng là các yêu cầu độc lập.

## 8. Chạy thử

```bash
./gradlew clean build
./gradlew bootRun
```

Gửi yêu cầu:

```bash
curl -X POST http://localhost:8080/api/orders/shipments \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId":"ORD-1001",
    "receiverName":"Nguyễn Văn A",
    "receiverAddress":"Hà Nội"
  }'
```

Test tự động xác nhận `max-attempts`, thời gian nghỉ 2 giây và bộ lọc chỉ retry `TimeoutException` được nạp đúng từ `application.yml`.
