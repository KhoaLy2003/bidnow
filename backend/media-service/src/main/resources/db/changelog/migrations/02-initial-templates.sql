-- liquibase formatted sql

-- changeset khoa.ly:2
-- 1. Welcome Email
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'WELCOME_EMAIL_EN', 'EMAIL', 'EN', 'Welcome to BidNow, {userName}!',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Welcome to BidNow!</h2><p>Hello <strong>{userName}</strong>,</p><p>We are thrilled to have you on board. Get ready to discover unique items and place winning bids!</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; border-radius: 5px;">Explore Auctions</a><p>Happy Bidding!<br>The BidNow Team</p></div>',
        'Hello {userName},\n\nWelcome to BidNow! We are thrilled to have you on board.\nTo get started, please explore our latest auctions here: {actionUrl}\n\nHappy Bidding!\nThe BidNow Team',
        '["userName", "actionUrl"]', true),

       (gen_random_uuid(), 'WELCOME_EMAIL_VI', 'EMAIL', 'VI', 'Chào mừng bạn đến với BidNow, {userName}!',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Chào mừng đến với BidNow!</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Chúng tôi rất vui khi có bạn tham gia. Hãy sẵn sàng khám phá những món đồ độc đáo và đặt giá chiến thắng!</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; border-radius: 5px;">Khám phá Đấu giá</a><p>Chúc bạn đấu giá vui vẻ!<br>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nChào mừng bạn đến với BidNow! Chúng tôi rất vui khi có bạn tham gia.\nĐể bắt đầu, hãy khám phá các phiên đấu giá mới nhất tại đây: {actionUrl}\n\nChúc bạn đấu giá vui vẻ!\nĐội ngũ BidNow',
        '["userName", "actionUrl"]', true);

-- 2. Auction Created
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'AUCTION_CREATED_EN', 'EMAIL', 'EN', 'Your Auction "{auctionTitle}" is Now Live!',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Auction Live!</h2><p>Hello <strong>{userName}</strong>,</p><p>Great news! Your auction for <strong>"{auctionTitle}"</strong> has been successfully created and is now live.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View Auction</a><p>Good luck!<br>The BidNow Team</p></div>',
        'Hello {userName},\n\nGreat news! Your auction for "{auctionTitle}" has been successfully created and is now live.\nYou can view and manage your auction here: {actionUrl}\n\nGood luck!\nThe BidNow Team',
        '["userName", "auctionTitle", "actionUrl"]', true),

       (gen_random_uuid(), 'AUCTION_CREATED_VI', 'EMAIL', 'VI', 'Phiên đấu giá "{auctionTitle}" của bạn đã bắt đầu!',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Phiên đấu giá đã bắt đầu!</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Tin vui! Phiên đấu giá cho <strong>"{auctionTitle}"</strong> của bạn đã được tạo thành công và đang diễn ra.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem Phiên đấu giá</a><p>Chúc bạn may mắn!<br>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nTin vui! Phiên đấu giá cho "{auctionTitle}" của bạn đã được tạo thành công và đang diễn ra.\nBạn có thể xem và quản lý phiên đấu giá của mình tại đây: {actionUrl}\n\nChúc bạn may mắn!\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "actionUrl"]', true);

-- 3. Auction Won
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'AUCTION_WON_EN', 'EMAIL', 'EN', 'Congratulations! You won "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Congratulations! You Won!</h2><p>Hello <strong>{userName}</strong>,</p><p>You have won the auction for <strong>"{auctionTitle}"</strong> with a winning bid of <strong>{bidAmount}</strong>.</p><p style="color: #dc3545;">Please complete your payment before <strong>{paymentDeadline}</strong> to secure your item.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Complete Payment</a><p>Thank you for using BidNow!</p></div>',
        'Hello {userName},\n\nCongratulations! You have won the auction for "{auctionTitle}" with a winning bid of {bidAmount}.\nPlease complete your payment before {paymentDeadline} to secure your item.\nComplete payment here: {actionUrl}\n\nThank you for using BidNow!',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true),

       (gen_random_uuid(), 'AUCTION_WON_VI', 'EMAIL', 'VI', 'Chúc mừng! Bạn đã chiến thắng "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Chúc mừng! Bạn đã chiến thắng!</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Bạn đã chiến thắng phiên đấu giá <strong>"{auctionTitle}"</strong> với mức giá <strong>{bidAmount}</strong>.</p><p style="color: #dc3545;">Vui lòng hoàn tất thanh toán trước <strong>{paymentDeadline}</strong> để nhận sản phẩm.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Thanh toán ngay</a><p>Cảm ơn bạn đã sử dụng BidNow!</p></div>',
        'Xin chào {userName},\n\nChúc mừng! Bạn đã chiến thắng phiên đấu giá "{auctionTitle}" với mức giá {bidAmount}.\nVui lòng hoàn tất thanh toán trước {paymentDeadline} để nhận sản phẩm.\nThanh toán tại đây: {actionUrl}\n\nCảm ơn bạn đã sử dụng BidNow!',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true);

-- 4. Auction Lost
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'AUCTION_LOST_EN', 'EMAIL', 'EN', 'Update on "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Auction Update</h2><p>Hello <strong>{userName}</strong>,</p><p>The auction for <strong>"{auctionTitle}"</strong> has ended, and unfortunately, you were not the highest bidder this time.</p><p>Don''t worry! There are many other items waiting for you.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Find Similar Items</a><p>Better luck next time!<br>The BidNow Team</p></div>',
        'Hello {userName},\n\nThe auction for "{auctionTitle}" has ended, and unfortunately, you were not the highest bidder this time.\nDon''t worry! There are many other items waiting for you.\nFind similar items here: {actionUrl}\n\nBetter luck next time!\nThe BidNow Team',
        '["userName", "auctionTitle", "actionUrl"]', true),

       (gen_random_uuid(), 'AUCTION_LOST_VI', 'EMAIL', 'VI', 'Cập nhật về phiên đấu giá "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Cập nhật Đấu giá</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Phiên đấu giá <strong>"{auctionTitle}"</strong> đã kết thúc, và rất tiếc bạn không phải là người trả giá cao nhất lần này.</p><p>Đừng lo! Còn rất nhiều sản phẩm khác đang chờ bạn.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Tìm sản phẩm tương tự</a><p>Chúc bạn may mắn lần sau!<br>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nPhiên đấu giá "{auctionTitle}" đã kết thúc, và rất tiếc bạn không phải là người trả giá cao nhất lần này.\nĐừng lo! Còn rất nhiều sản phẩm khác đang chờ bạn.\nTìm các sản phẩm tương tự tại đây: {actionUrl}\n\nChúc bạn may mắn lần sau!\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "actionUrl"]', true);

-- 5. Payment Reminder 1
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'PAYMENT_REMINDER_1_EN', 'EMAIL', 'EN', 'Reminder: Payment due for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Payment Reminder</h2><p>Hello <strong>{userName}</strong>,</p><p>This is a friendly reminder that your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong> is due.</p><p>Please complete the payment by <strong>{paymentDeadline}</strong> to avoid losing the item and your deposit.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Pay Now</a><p>Thank you!<br>The BidNow Team</p></div>',
        'Hello {userName},\n\nThis is a friendly reminder that your payment of {bidAmount} for "{auctionTitle}" is due.\nPlease complete the payment by {paymentDeadline} to avoid losing the item and your deposit.\nPay now: {actionUrl}\n\nThank you!\nThe BidNow Team',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true),

       (gen_random_uuid(), 'PAYMENT_REMINDER_1_VI', 'EMAIL', 'VI', 'Nhắc nhở: Thanh toán cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Nhắc nhở Thanh toán</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Đây là thông báo nhắc nhở về khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>.</p><p>Vui lòng hoàn tất thanh toán trước <strong>{paymentDeadline}</strong> để tránh mất sản phẩm và tiền cọc.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #ffc107; color: #333; text-decoration: none; border-radius: 5px;">Thanh toán ngay</a><p>Cảm ơn bạn!<br>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nĐây là thông báo nhắc nhở về khoản thanh toán {bidAmount} cho "{auctionTitle}".\nVui lòng hoàn tất thanh toán trước {paymentDeadline} để tránh mất sản phẩm và tiền cọc.\nThanh toán ngay: {actionUrl}\n\nCảm ơn bạn!\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true);

-- 6. Payment Reminder 2
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'PAYMENT_REMINDER_2_EN', 'EMAIL', 'EN', 'URGENT: Final notice to pay for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">URGENT: Final Payment Notice</h2><p>Hello <strong>{userName}</strong>,</p><p>Your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong> is almost overdue!</p><p>If payment is not received by <strong>{paymentDeadline}</strong>, your bid will be cancelled and your deposit will be forfeited.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #dc3545; color: #fff; text-decoration: none; border-radius: 5px;">Pay Immediately</a><p>The BidNow Team</p></div>',
        'Hello {userName},\n\nURGENT: Your payment of {bidAmount} for "{auctionTitle}" is almost overdue!\nIf payment is not received by {paymentDeadline}, your bid will be cancelled and your deposit will be forfeited.\nPay immediately here: {actionUrl}\n\nThe BidNow Team',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true),

       (gen_random_uuid(), 'PAYMENT_REMINDER_2_VI', 'EMAIL', 'VI',
        'KHẨN CẤP: Thông báo cuối cùng để thanh toán "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">KHẨN CẤP: Thông báo Thanh toán Cuối cùng</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong> của bạn sắp hết hạn!</p><p>Nếu không nhận được thanh toán trước <strong>{paymentDeadline}</strong>, kết quả đấu giá sẽ bị hủy và bạn sẽ mất tiền cọc.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #dc3545; color: #fff; text-decoration: none; border-radius: 5px;">Thanh toán ngay lập tức</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nKHẨN CẤP: Khoản thanh toán {bidAmount} cho "{auctionTitle}" của bạn sắp hết hạn!\nNếu không nhận được thanh toán trước {paymentDeadline}, kết quả đấu giá sẽ bị hủy và bạn sẽ mất tiền cọc.\nThanh toán ngay lập tức tại đây: {actionUrl}\n\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "bidAmount", "actionUrl", "paymentDeadline"]', true);

-- 7. Payment Successful
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'PAYMENT_SUCCESSFUL_EN', 'EMAIL', 'EN', 'Payment Received for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Payment Successful!</h2><p>Hello <strong>{userName}</strong>,</p><p>Thank you! We have successfully received your payment of <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong>.</p><p>The seller will now prepare your item for shipment.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View Order Details</a><p>Thank you for choosing BidNow!</p></div>',
        'Hello {userName},\n\nThank you! We have successfully received your payment of {bidAmount} for "{auctionTitle}".\nThe seller will now prepare your item for shipment.\nView order details here: {actionUrl}\n\nThank you for choosing BidNow!',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true),

       (gen_random_uuid(), 'PAYMENT_SUCCESSFUL_VI', 'EMAIL', 'VI', 'Thanh toán thành công cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Thanh toán Thành công!</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Cảm ơn bạn! Chúng tôi đã nhận được khoản thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>.</p><p>Người bán sẽ chuẩn bị gửi hàng cho bạn.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem Chi tiết Đơn hàng</a><p>Cảm ơn bạn đã chọn BidNow!</p></div>',
        'Xin chào {userName},\n\nCảm ơn bạn! Chúng tôi đã nhận được khoản thanh toán {bidAmount} cho "{auctionTitle}".\nNgười bán sẽ chuẩn bị gửi hàng cho bạn.\nXem chi tiết đơn hàng tại đây: {actionUrl}\n\nCảm ơn bạn đã chọn BidNow!',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true);

-- 8. Deposit Refunded
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'DEPOSIT_REFUNDED_EN', 'EMAIL', 'EN', 'Your Deposit for "{auctionTitle}" has been Refunded',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Deposit Refunded</h2><p>Hello <strong>{userName}</strong>,</p><p>Because you did not win the auction for <strong>"{auctionTitle}"</strong>, your deposit has been fully refunded to your wallet.</p><p>You can use it for other exciting auctions!</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Go To Wallet</a><p>See you soon!<br>The BidNow Team</p></div>',
        'Hello {userName},\n\nBecause you did not win the auction for "{auctionTitle}", your deposit has been fully refunded to your wallet.\nYou can use it for other exciting auctions!\nGo to your wallet here: {actionUrl}\n\nSee you soon!\nThe BidNow Team',
        '["userName", "auctionTitle", "actionUrl"]', true),

       (gen_random_uuid(), 'DEPOSIT_REFUNDED_VI', 'EMAIL', 'VI', 'Tiền cọc cho "{auctionTitle}" đã được hoàn lại',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Đã hoàn Tiền cọc</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Vì bạn không chiến thắng phiên đấu giá <strong>"{auctionTitle}"</strong>, tiền cọc của bạn đã được hoàn trả đầy đủ vào ví.</p><p>Bạn có thể sử dụng nó cho các phiên đấu giá hấp dẫn khác!</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Đến Ví của bạn</a><p>Hẹn gặp lại bạn sớm!<br>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},\n\nVì bạn không chiến thắng phiên đấu giá "{auctionTitle}", tiền cọc của bạn đã được hoàn trả đầy đủ vào ví.\nBạn có thể sử dụng nó cho các phiên đấu giá hấp dẫn khác!\nĐến ví của bạn tại đây: {actionUrl}\n\nHẹn gặp lại bạn sớm!\nĐội ngũ BidNow',
        '["userName", "auctionTitle", "actionUrl"]', true);
