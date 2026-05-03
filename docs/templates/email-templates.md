# BidNow Email Templates Preview

This document contains the initial email templates for the BidNow platform in English (EN) and Vietnamese (VI).
These will be inserted into the `notif_notification_templates` table via Liquibase migration after approval.

---

## 1. Welcome Email (User Registration)
**Template Name:** `WELCOME_EMAIL`
**Variables:** `{userName}`, `{actionUrl}`

### English (EN)
**Subject:** Welcome to BidNow, {userName}!
**Body Text:**
```text
Hello {userName},

Welcome to BidNow! We are thrilled to have you on board.
To get started, please explore our latest auctions here: {actionUrl}

Happy Bidding!
The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Welcome to BidNow!</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>We are thrilled to have you on board. Get ready to discover unique items and place winning bids!</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; border-radius: 5px;">Explore Auctions</a>
  <p>Happy Bidding!<br>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** Chào mừng bạn đến với BidNow, {userName}!
**Body Text:**
```text
Xin chào {userName},

Chào mừng bạn đến với BidNow! Chúng tôi rất vui khi có bạn tham gia.
Để bắt đầu, hãy khám phá các phiên đấu giá mới nhất tại đây: {actionUrl}

Chúc bạn đấu giá vui vẻ!
Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Chào mừng đến với BidNow!</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Chúng tôi rất vui khi có bạn tham gia. Hãy sẵn sàng khám phá những món đồ độc đáo và đặt giá chiến thắng!</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; border-radius: 5px;">Khám phá Đấu giá</a>
  <p>Chúc bạn đấu giá vui vẻ!<br>Đội ngũ BidNow</p>
</div>
```

---

## 2. Auction Created Successfully
**Template Name:** `AUCTION_CREATED`
**Variables:** `{userName}`, `{auctionTitle}`, `{actionUrl}`

### English (EN)
**Subject:** Your Auction "{auctionTitle}" is Now Live!
**Body Text:**
```text
Hello {userName},

Great news! Your auction for "{auctionTitle}" has been successfully created and is now live.
You can view and manage your auction here: {actionUrl}

Good luck!
The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Auction Live!</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>Great news! Your auction for <strong>"{auctionTitle}"</strong> has been successfully created and is now live.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View Auction</a>
  <p>Good luck!<br>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** Phiên đấu giá "{auctionTitle}" của bạn đã bắt đầu!
**Body Text:**
```text
Xin chào {userName},

Tin vui! Phiên đấu giá cho "{auctionTitle}" của bạn đã được tạo thành công và đang diễn ra.
Bạn có thể xem và quản lý phiên đấu giá của mình tại đây: {actionUrl}

Chúc bạn may mắn!
Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Phiên đấu giá đã bắt đầu!</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Tin vui! Phiên đấu giá cho <strong>"{auctionTitle}"</strong> của bạn đã được tạo thành công và đang diễn ra.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem Phiên đấu giá</a>
  <p>Chúc bạn may mắn!<br>Đội ngũ BidNow</p>
</div>
```

---

## 3. Auction Won
**Template Name:** `AUCTION_WON`
**Variables:** `{userName}`, `{auctionTitle}`, `{bidAmount}`, `{actionUrl}`, `{paymentDeadline}`

### English (EN)
**Subject:** Congratulations! You won "{auctionTitle}"
**Body Text:**
```text
Hello {userName},

Congratulations! You have won the auction for "{auctionTitle}" with a winning bid of {bidAmount}.
Please complete your payment before {paymentDeadline} to secure your item.
Complete payment here: {actionUrl}

Thank you for using BidNow!
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Congratulations! You Won!</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>You have won the auction for <strong>"{auctionTitle}"</strong> with a winning bid of <strong>{bidAmount}</strong>.</p>
  <p style="color: #dc3545;">Please complete your payment before <strong>{paymentDeadline}</strong> to secure your item.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Complete Payment</a>
  <p>Thank you for using BidNow!</p>
</div>
```

### Vietnamese (VI)
**Subject:** Chúc mừng! Bạn đã chiến thắng "{auctionTitle}"
**Body Text:**
```text
Xin chào {userName},

Chúc mừng! Bạn đã chiến thắng phiên đấu giá "{auctionTitle}" với mức giá {bidAmount}.
Vui lòng hoàn tất thanh toán trước {paymentDeadline} để nhận sản phẩm.
Thanh toán tại đây: {actionUrl}

Cảm ơn bạn đã sử dụng BidNow!
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Chúc mừng! Bạn đã chiến thắng!</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Bạn đã chiến thắng phiên đấu giá <strong>"{auctionTitle}"</strong> với mức giá <strong>{bidAmount}</strong>.</p>
  <p style="color: #dc3545;">Vui lòng hoàn tất thanh toán trước <strong>{paymentDeadline}</strong> để nhận sản phẩm.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Thanh toán ngay</a>
  <p>Cảm ơn bạn đã sử dụng BidNow!</p>
</div>
```

---

## 4. Auction Lost
**Template Name:** `AUCTION_LOST`
**Variables:** `{userName}`, `{auctionTitle}`, `{actionUrl}`

### English (EN)
**Subject:** Update on "{auctionTitle}"
**Body Text:**
```text
Hello {userName},

The auction for "{auctionTitle}" has ended, and unfortunately, you were not the highest bidder this time.
Don't worry! There are many other items waiting for you.
Find similar items here: {actionUrl}

Better luck next time!
The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Auction Update</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>The auction for <strong>"{auctionTitle}"</strong> has ended, and unfortunately, you were not the highest bidder this time.</p>
  <p>Don't worry! There are many other items waiting for you.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Find Similar Items</a>
  <p>Better luck next time!<br>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** Cập nhật về phiên đấu giá "{auctionTitle}"
**Body Text:**
```text
Xin chào {userName},

Phiên đấu giá "{auctionTitle}" đã kết thúc, và rất tiếc bạn không phải là người trả giá cao nhất lần này.
Đừng lo! Còn rất nhiều sản phẩm khác đang chờ bạn.
Tìm các sản phẩm tương tự tại đây: {actionUrl}

Chúc bạn may mắn lần sau!
Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Cập nhật Đấu giá</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Phiên đấu giá <strong>"{auctionTitle}"</strong> đã kết thúc, và rất tiếc bạn không phải là người trả giá cao nhất lần này.</p>
  <p>Đừng lo! Còn rất nhiều sản phẩm khác đang chờ bạn.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Tìm sản phẩm tương tự</a>
  <p>Chúc bạn may mắn lần sau!<br>Đội ngũ BidNow</p>
</div>
```

---

## 5. Payment Reminder #1 (24 Hours)
**Template Name:** `PAYMENT_REMINDER_1`
**Variables:** `{userName}`, `{auctionTitle}`, `{bidAmount}`, `{actionUrl}`, `{paymentDeadline}`

### English (EN)
**Subject:** Reminder: Payment due for "{auctionTitle}"
**Body Text:**
```text
Hello {userName},

This is a friendly reminder that your payment of {bidAmount} for "{auctionTitle}" is due.
Please complete the payment by {paymentDeadline} to avoid losing the item and your deposit.
Pay now: {actionUrl}

Thank you!
The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Payment Reminder</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>This is a friendly reminder that your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong> is due.</p>
  <p>Please complete the payment by <strong>{paymentDeadline}</strong> to avoid losing the item and your deposit.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Pay Now</a>
  <p>Thank you!<br>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** Nhắc nhở: Thanh toán cho "{auctionTitle}"
**Body Text:**
```text
Xin chào {userName},

Đây là thông báo nhắc nhở về khoản thanh toán {bidAmount} cho "{auctionTitle}".
Vui lòng hoàn tất thanh toán trước {paymentDeadline} để tránh mất sản phẩm và tiền cọc.
Thanh toán ngay: {actionUrl}

Cảm ơn bạn!
Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Nhắc nhở Thanh toán</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Đây là thông báo nhắc nhở về khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>.</p>
  <p>Vui lòng hoàn tất thanh toán trước <strong>{paymentDeadline}</strong> để tránh mất sản phẩm và tiền cọc.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Thanh toán ngay</a>
  <p>Cảm ơn bạn!<br>Đội ngũ BidNow</p>
</div>
```

---

## 6. Payment Reminder #2 (Urgent)
**Template Name:** `PAYMENT_REMINDER_2`
**Variables:** `{userName}`, `{auctionTitle}`, `{bidAmount}`, `{actionUrl}`, `{paymentDeadline}`

### English (EN)
**Subject:** URGENT: Final notice to pay for "{auctionTitle}"
**Body Text:**
```text
Hello {userName},

URGENT: Your payment of {bidAmount} for "{auctionTitle}" is almost overdue!
If payment is not received by {paymentDeadline}, your bid will be cancelled and your deposit will be forfeited.
Pay immediately here: {actionUrl}

The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2 style="color: #dc3545;">URGENT: Final Payment Notice</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>Your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong> is almost overdue!</p>
  <p>If payment is not received by <strong>{paymentDeadline}</strong>, your bid will be cancelled and your deposit will be forfeited.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #dc3545; color: #fff; text-decoration: none; border-radius: 5px;">Pay Immediately</a>
  <p>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** KHẨN CẤP: Thông báo cuối cùng để thanh toán "{auctionTitle}"
**Body Text:**
```text
Xin chào {userName},

KHẨN CẤP: Khoản thanh toán {bidAmount} cho "{auctionTitle}" của bạn sắp hết hạn!
Nếu không nhận được thanh toán trước {paymentDeadline}, kết quả đấu giá sẽ bị hủy và bạn sẽ mất tiền cọc.
Thanh toán ngay lập tức tại đây: {actionUrl}

Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2 style="color: #dc3545;">KHẨN CẤP: Thông báo Thanh toán Cuối cùng</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong> của bạn sắp hết hạn!</p>
  <p>Nếu không nhận được thanh toán trước <strong>{paymentDeadline}</strong>, kết quả đấu giá sẽ bị hủy và bạn sẽ mất tiền cọc.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #dc3545; color: #fff; text-decoration: none; border-radius: 5px;">Thanh toán ngay lập tức</a>
  <p>Đội ngũ BidNow</p>
</div>
```

---

## 7. Payment Successful
**Template Name:** `PAYMENT_SUCCESSFUL`
**Variables:** `{userName}`, `{auctionTitle}`, `{bidAmount}`, `{actionUrl}`

### English (EN)
**Subject:** Payment Received for "{auctionTitle}"
**Body Text:**
```text
Hello {userName},

Thank you! We have successfully received your payment of {bidAmount} for "{auctionTitle}".
The seller will now prepare your item for shipment.
View order details here: {actionUrl}

Thank you for choosing BidNow!
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Payment Successful!</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>Thank you! We have successfully received your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong>.</p>
  <p>The seller will now prepare your item for shipment.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View Order Details</a>
  <p>Thank you for choosing BidNow!</p>
</div>
```

### Vietnamese (VI)
**Subject:** Thanh toán thành công cho "{auctionTitle}"
**Body Text:**
```text
Xin chào {userName},

Cảm ơn bạn! Chúng tôi đã nhận được khoản thanh toán {bidAmount} cho "{auctionTitle}".
Người bán sẽ chuẩn bị gửi hàng cho bạn.
Xem chi tiết đơn hàng tại đây: {actionUrl}

Cảm ơn bạn đã chọn BidNow!
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Thanh toán Thành công!</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Cảm ơn bạn! Chúng tôi đã nhận được khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>.</p>
  <p>Người bán sẽ chuẩn bị gửi hàng cho bạn.</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem Chi tiết Đơn hàng</a>
  <p>Cảm ơn bạn đã chọn BidNow!</p>
</div>
```

---

## 8. Deposit Refunded
**Template Name:** `DEPOSIT_REFUNDED`
**Variables:** `{userName}`, `{auctionTitle}`, `{actionUrl}`

### English (EN)
**Subject:** Your Deposit for "{auctionTitle}" has been Refunded
**Body Text:**
```text
Hello {userName},

Because you did not win the auction for "{auctionTitle}", your deposit has been fully refunded to your wallet.
You can use it for other exciting auctions!
Go to your wallet here: {actionUrl}

See you soon!
The BidNow Team
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Deposit Refunded</h2>
  <p>Hello <strong>{userName}</strong>,</p>
  <p>Because you did not win the auction for <strong>"{auctionTitle}"</strong>, your deposit has been fully refunded to your wallet.</p>
  <p>You can use it for other exciting auctions!</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Go To Wallet</a>
  <p>See you soon!<br>The BidNow Team</p>
</div>
```

### Vietnamese (VI)
**Subject:** Tiền cọc cho "{auctionTitle}" đã được hoàn lại
**Body Text:**
```text
Xin chào {userName},

Vì bạn không chiến thắng phiên đấu giá "{auctionTitle}", tiền cọc của bạn đã được hoàn trả đầy đủ vào ví.
Bạn có thể sử dụng nó cho các phiên đấu giá hấp dẫn khác!
Đến ví của bạn tại đây: {actionUrl}

Hẹn gặp lại bạn sớm!
Đội ngũ BidNow
```
**Body HTML:**
```html
<div style="font-family: Arial, sans-serif; color: #333;">
  <h2>Đã hoàn Tiền cọc</h2>
  <p>Xin chào <strong>{userName}</strong>,</p>
  <p>Vì bạn không chiến thắng phiên đấu giá <strong>"{auctionTitle}"</strong>, tiền cọc của bạn đã được hoàn trả đầy đủ vào ví.</p>
  <p>Bạn có thể sử dụng nó cho các phiên đấu giá hấp dẫn khác!</p>
  <a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Đến Ví của bạn</a>
  <p>Hẹn gặp lại bạn sớm!<br>Đội ngũ BidNow</p>
</div>
```
