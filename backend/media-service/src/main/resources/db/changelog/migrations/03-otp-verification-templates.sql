-- liquibase formatted sql

-- changeset khoa.ly:1778374394422
-- OTP Verification Email (EN)
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'OTP_VERIFICATION_EN', 'EMAIL', 'EN',
        'Your BidNow Verification Code',
        '<div style="font-family: Arial, sans-serif; color: #333; max-width: 480px; margin: 0 auto;"><h2 style="color: #007bff;">Verify Your Email</h2><p>Hello,</p><p>Use the verification code below to complete your BidNow registration. This code is valid for <strong>10 minutes</strong>.</p><div style="text-align: center; margin: 32px 0;"><span style="display: inline-block; font-size: 36px; font-weight: bold; letter-spacing: 12px; color: #007bff; background: #f0f4ff; padding: 16px 32px; border-radius: 8px;">{otp}</span></div><p style="color: #888; font-size: 13px;">If you did not create a BidNow account, you can safely ignore this email.</p><p>The BidNow Team</p></div>',
        'Hello,\n\nUse the verification code below to complete your BidNow registration.\nThis code is valid for 10 minutes.\n\nYour OTP: {otp}\n\nIf you did not create a BidNow account, you can safely ignore this email.\n\nThe BidNow Team',
        '["otp"]', true);

-- OTP Verification Email (VI)
INSERT INTO notif_notification_templates (id, name, type, language, subject, body_html, body_text, variables, active)
VALUES (gen_random_uuid(), 'OTP_VERIFICATION_VI', 'EMAIL', 'VI',
        'Mã xác minh BidNow của bạn',
        '<div style="font-family: Arial, sans-serif; color: #333; max-width: 480px; margin: 0 auto;"><h2 style="color: #007bff;">Xác minh Email của bạn</h2><p>Xin chào,</p><p>Sử dụng mã xác minh bên dưới để hoàn tất đăng ký BidNow. Mã này có hiệu lực trong <strong>10 phút</strong>.</p><div style="text-align: center; margin: 32px 0;"><span style="display: inline-block; font-size: 36px; font-weight: bold; letter-spacing: 12px; color: #007bff; background: #f0f4ff; padding: 16px 32px; border-radius: 8px;">{otp}</span></div><p style="color: #888; font-size: 13px;">Nếu bạn không tạo tài khoản BidNow, hãy bỏ qua email này.</p><p>Đội ngũ BidNow</p></div>',
        'Xin chào,\n\nSử dụng mã xác minh bên dưới để hoàn tất đăng ký BidNow.\nMã này có hiệu lực trong 10 phút.\n\nMã OTP của bạn: {otp}\n\nNếu bạn không tạo tài khoản BidNow, hãy bỏ qua email này.\n\nĐội ngũ BidNow',
        '["otp"]', true);
