-- liquibase formatted sql

-- Variables per template (NotificationTemplatesPostgresIT checks that handlers supply them all):
--   AUCTION_CANCELLED_{EN,VI}: userName, auctionTitle, actionUrl
--   PAYMENT_FAILED_{EN,VI}:    userName, auctionTitle, depositAmount, actionUrl
--   SALE_PAYMENT_RECEIVED_{EN,VI}: userName, auctionTitle, bidAmount, actionUrl

-- changeset hiep.nguyen:event-templates-auction-cancelled
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'AUCTION_CANCELLED_EN', 'EMAIL', 'EN', 'Auction "{auctionTitle}" was cancelled',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Auction Cancelled</h2><p>Hello <strong>{userName}</strong>,</p><p>The auction for <strong>"{auctionTitle}"</strong> you bid on has been cancelled.</p><p>Any deposit you placed is being refunded to your wallet.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Go To Wallet</a><p>The BidNow Team</p></div>',
        'Hello {userName},

The auction for "{auctionTitle}" you bid on has been cancelled.
Any deposit you placed is being refunded to your wallet: {actionUrl}

The BidNow Team',
        '["userName", "auctionTitle", "actionUrl"]', true),

       (gen_random_uuid(), 'AUCTION_CANCELLED_VI', 'EMAIL', 'VI', 'Phiên đấu giá "{auctionTitle}" đã bị hủy',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Phiên đấu giá đã bị hủy</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Phiên đấu giá <strong>"{auctionTitle}"</strong> mà bạn tham gia đã bị hủy.</p><p>Tiền cọc của bạn (nếu có) đang được hoàn về ví.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #17a2b8; color: #fff; text-decoration: none; border-radius: 5px;">Đến Ví của bạn</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},

Phiên đấu giá "{auctionTitle}" mà bạn tham gia đã bị hủy.
Tiền cọc của bạn (nếu có) đang được hoàn về ví: {actionUrl}

Đội ngũ BidNow',
        '["userName", "auctionTitle", "actionUrl"]', true);

-- changeset hiep.nguyen:event-templates-payment-failed
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'PAYMENT_FAILED_EN', 'EMAIL', 'EN', 'Payment deadline missed for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">Payment Deadline Missed</h2><p>Hello <strong>{userName}</strong>,</p><p>The payment deadline for <strong>"{auctionTitle}"</strong> has passed, so your win has been cancelled.</p><p>Your deposit of <strong>{depositAmount}</strong> has been forfeited.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #6c757d; color: #fff; text-decoration: none; border-radius: 5px;">View Wallet</a><p>The BidNow Team</p></div>',
        'Hello {userName},

The payment deadline for "{auctionTitle}" has passed, so your win has been cancelled.
Your deposit of {depositAmount} has been forfeited.
View your wallet: {actionUrl}

The BidNow Team',
        '["userName", "auctionTitle", "depositAmount", "actionUrl"]', true),

       (gen_random_uuid(), 'PAYMENT_FAILED_VI', 'EMAIL', 'VI', 'Đã quá hạn thanh toán cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2 style="color: #dc3545;">Đã quá hạn thanh toán</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Đã quá hạn thanh toán cho <strong>"{auctionTitle}"</strong>, vì vậy kết quả thắng của bạn đã bị hủy.</p><p>Tiền cọc <strong>{depositAmount}</strong> của bạn đã bị tịch thu.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #6c757d; color: #fff; text-decoration: none; border-radius: 5px;">Xem Ví</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},

Đã quá hạn thanh toán cho "{auctionTitle}", vì vậy kết quả thắng của bạn đã bị hủy.
Tiền cọc {depositAmount} của bạn đã bị tịch thu.
Xem ví của bạn: {actionUrl}

Đội ngũ BidNow',
        '["userName", "auctionTitle", "depositAmount", "actionUrl"]', true);

-- changeset hiep.nguyen:event-templates-sale-payment-received
INSERT INTO media_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'SALE_PAYMENT_RECEIVED_EN', 'EMAIL', 'EN', 'The buyer paid for "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Payment Received</h2><p>Hello <strong>{userName}</strong>,</p><p>The buyer has paid <strong>{bidAmount}</strong> for <strong>"{auctionTitle}"</strong>. The amount has been credited to your wallet.</p><p>Please prepare the item for shipment.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">View My Auctions</a><p>The BidNow Team</p></div>',
        'Hello {userName},

The buyer has paid {bidAmount} for "{auctionTitle}". The amount has been credited to your wallet.
Please prepare the item for shipment: {actionUrl}

The BidNow Team',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true),

       (gen_random_uuid(), 'SALE_PAYMENT_RECEIVED_VI', 'EMAIL', 'VI', 'Người mua đã thanh toán cho "{auctionTitle}"',
        '<div style="font-family: Arial, sans-serif; color: #333;"><h2>Đã nhận thanh toán</h2><p>Xin chào <strong>{userName}</strong>,</p><p>Người mua đã thanh toán <strong>{bidAmount}</strong> cho <strong>"{auctionTitle}"</strong>. Số tiền đã được cộng vào ví của bạn.</p><p>Vui lòng chuẩn bị gửi hàng.</p><a href="{actionUrl}" style="display: inline-block; padding: 10px 20px; background-color: #28a745; color: #fff; text-decoration: none; border-radius: 5px;">Xem phiên đấu giá của tôi</a><p>Đội ngũ BidNow</p></div>',
        'Xin chào {userName},

Người mua đã thanh toán {bidAmount} cho "{auctionTitle}". Số tiền đã được cộng vào ví của bạn.
Vui lòng chuẩn bị gửi hàng: {actionUrl}

Đội ngũ BidNow',
        '["userName", "auctionTitle", "bidAmount", "actionUrl"]', true);
