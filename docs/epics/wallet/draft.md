Tuyệt vời! Cùng thảo luận về Wallet Service nhé. Dựa trên docs bạn cung cấp, tôi thấy Wallet Service đóng vai trò **cực kỳ quan trọng** trong luồng đấu giá. Hãy phân tích kỹ:

---

## 🎯 Phân Tích Core Features liên quan đến Wallet

Từ functional requirements, tôi extract được các luồng chính:

### 1. **Deposit Management** (Quản lý Tiền Gửi/Cọc)

- User phải có deposit **trước khi** bid
- Deposit có thể là:
  - **Flat fee** (phí cố định mỗi auction)
  - **Percentage** (% của starting price)
- Cần **lock deposit** khi user tham gia auction

### 2. **Wallet Balance Operations**

- Nạp tiền vào wallet (deposit funds)
- Rút tiền từ wallet (withdraw - có thể phase 2)
- Xem transaction history
- Check balance realtime

### 3. **Escrow & Refund Logic** (Ký quỹ & Hoàn tiền)

- **Auto refund** deposit cho losing bidders khi auction kết thúc
- **Hold funds** của winner trong khoảng thời gian thanh toán (24-48h)
- **Forfeit deposit** nếu winner không thanh toán đúng hạn

### 4. **Payment Processing**

- Winner phải complete payment từ wallet
- Deduct full amount từ wallet → Transfer đến seller
- Platform fee (commission) - nếu có

---

## 📋 Giai Đoạn 1: Core Features (Mock Payment Gateway)

### **Phase 1.1: Basic Wallet Operations**

#### Feature 1: **Wallet Initialization**

- Tự động tạo wallet khi user register (event-driven từ Identity Service)
- Initial balance = 0
- Wallet status: ACTIVE, FROZEN, CLOSED

#### Feature 2: **Mock Deposit (Nạp Tiền Giả Lập)**

```
Flow:
1. User request "Add 1,000,000 VND"
2. System shows mock payment gateway UI
3. User clicks "Confirm Payment" (giả lập thành công)
4. Wallet balance += 1,000,000
5. Create DEPOSIT transaction record
```

**Mock scenarios cần test:**

- ✅ Successful deposit
- ❌ Failed deposit (network timeout)
- ⏳ Pending deposit (user chưa confirm)

#### Feature 3: **View Balance & Transaction History**

- GET wallet balance
- GET transaction history (paginated)
  - Filter by type: DEPOSIT, REFUND, PAYMENT, FORFEIT
  - Filter by date range
  - Sort by timestamp

---

### **Phase 1.2: Auction Deposit Logic**

#### Feature 4: **Lock Deposit for Bidding**

```
Trigger: User places first bid on an auction

Flow:
1. Bidding Service → Request deposit lock từ Wallet Service
2. Wallet Service calculates required deposit:
   - Option A: Fixed amount (e.g., 50,000 VND)
   - Option B: 10% of starting price
3. Check if balance >= deposit
4. Create LOCKED transaction
5. available_balance = total_balance - locked_amount
6. Return success → Bidding Service allows bid
```

**Edge cases:**

- User bids on multiple auctions → Multiple locks
- Insufficient balance → Reject bid
- Concurrent lock requests → Use pessimistic locking

#### Feature 5: **Release Deposit (Refund Losing Bidders)**

```
Trigger: Auction ends (event from Auction Service)

Flow:
1. Wallet Service receives AUCTION_ENDED event
2. Identify all bidders (except winner)
3. For each losing bidder:
   - Find LOCKED transaction for this auction
   - Create REFUND transaction
   - available_balance += locked_amount
   - Update lock status = RELEASED
4. Notify users via Notification Service
```

---

### **Phase 1.3: Winner Payment Flow**

#### Feature 6: **Hold Winner's Funds**

```
Trigger: Auction ends → Winner identified

Flow:
1. Wallet Service receives AUCTION_WON event
2. Calculate final payment = winning_bid_amount
3. Check if winner's balance >= final_payment
4. Create HOLD transaction (status = PENDING_PAYMENT)
5. Set deadline = now + 48 hours
6. Notify winner: "Complete payment within 48h"
```

#### Feature 7: **Complete Payment**

```
Trigger: Winner confirms payment

Flow:
1. Winner clicks "Pay Now"
2. Deduct from winner's wallet
3. Transfer to seller's wallet (minus platform fee if any)
4. Update HOLD → COMPLETED
5. Release original deposit lock
6. Emit PAYMENT_COMPLETED event
```

#### Feature 8: **Forfeit Deposit (Winner Fails to Pay)**

```
Trigger: Payment deadline expires

Scheduled Job (runs every minute):
1. Find all PENDING_PAYMENT transactions where deadline < now
2. For each expired payment:
   - Deduct deposit from winner's wallet
   - Create FORFEIT transaction
   - Transfer forfeited amount to platform (or seller as penalty)
   - Update auction status = FAILED
   - Emit PAYMENT_FAILED event
```

---

### **Phase 1.4: Platform Fee & Seller Payouts**

#### Feature 9: **Calculate & Collect Platform Fee**

```
Example: 5% commission on final sale

When payment completes:
1. final_amount = winning_bid
2. platform_fee = final_amount * 0.05
3. seller_payout = final_amount - platform_fee
4. Transfer seller_payout to seller's wallet
5. Transfer platform_fee to platform wallet (special account)
```

#### Feature 10: **Seller Withdrawal (Optional Phase 1)**

- Seller can request withdraw funds từ wallet về bank account
- Mock withdrawal: Just deduct from wallet, log transaction
- Real withdrawal → Phase 2 với VNPay

---

## 📋 Giai Đoạn 2: Payment Gateway Integration (VNPay)

### **Phase 2.1: Real Deposit via VNPay**

#### Feature 11: **VNPay Deposit Integration**

```
Flow:
1. User clicks "Deposit"
2. Enter amount (e.g., 500,000 VND)
3. System generates VNPay payment URL
4. Redirect to VNPay gateway
5. User completes payment on VNPay
6. VNPay redirects back with IPN callback
7. Wallet Service verifies signature
8. Update wallet balance if valid
9. Store VNPay transaction ID for reconciliation
```

**VNPay specific:**

- Handle IPN (Instant Payment Notification)
- Verify checksums
- Handle các response codes (00 = success, 24 = cancel, etc.)
- Idempotency: Prevent duplicate processing

---

### **Phase 2.2: Real Withdrawal**

#### Feature 12: **VNPay Withdrawal**

```
Flow:
1. Seller requests withdrawal
2. Enter bank account info (số tài khoản, ngân hàng)
3. System initiates VNPay payout API
4. VNPay processes transfer
5. Update wallet balance when confirmed
6. Send confirmation email
```

**Considerations:**

- KYC verification trước khi withdrawal
- Withdrawal limits (min/max per transaction)
- Withdrawal fees
- Processing time (instant vs. batch processing)

---

### **Phase 2.3: Advanced Features**

#### Feature 13: **Wallet Statement & Reconciliation**

- Generate monthly statements (PDF)
- Export transactions to CSV
- Admin dashboard: Reconcile VNPay transactions với internal records

#### Feature 14: **Security Enhancements**

- 2FA for large transactions
- Transaction approval workflow
- Fraud detection (unusual patterns)

---

## 🔐 Data Model (Preliminary)

### **Wallet Table**

```sql
wallets:
  - id (PK)
  - user_id (FK, unique)
  - total_balance (DECIMAL)
  - available_balance (DECIMAL)  -- total - locked
  - currency (VARCHAR) -- "VND"
  - status (ENUM: ACTIVE, FROZEN, CLOSED)
  - created_at, updated_at
```

### **Transaction Table**

```sql
transactions:
  - id (PK)
  - wallet_id (FK)
  - type (ENUM: DEPOSIT, REFUND, PAYMENT, FORFEIT, WITHDRAWAL, FEE)
  - amount (DECIMAL)
  - balance_before (DECIMAL)
  - balance_after (DECIMAL)
  - reference_id (auction_id, bid_id, etc.)
  - status (ENUM: PENDING, COMPLETED, FAILED)
  - payment_gateway_tx_id (VNPay transaction ID)
  - metadata (JSONB) -- Extra info
  - created_at
```

### **Deposit Lock Table** (Tracking locked funds)

```sql
deposit_locks:
  - id (PK)
  - wallet_id (FK)
  - auction_id (FK)
  - amount (DECIMAL)
  - status (ENUM: LOCKED, RELEASED, FORFEITED)
  - locked_at
  - released_at
```

---

## 🤔 Các Câu Hỏi Cần Làm Rõ

1. **Deposit amount calculation:**
   - Fixed fee hay percentage? => fixed fee do seller quy định khi tạo auction
   - Nếu percentage → Min/max thresholds?
2. **Platform fee:**
   - % bao nhiêu?
   - Ai chịu fee: Buyer, Seller, hay split?
3. **Refund timing:**
   - Refund ngay khi auction ends hay có delay? => trong vòng 1h sau khi auction kết thúc
   - Batch processing hay realtime? => batch processing
4. **Currency:**
   - Chỉ VND hay support multiple currencies (USD, EUR)? => multiple currencies support (but for MVP => VND)
5. **Withdrawal limits:**
   - Min withdrawal amount? => no limit
   - Max per day/week? => no limit
6. **Failed payment penalty:**
   - Forfeited deposit về đâu? (Platform, Seller, hay burn?) => seller
   - Winner bị ban tạm thời không? => có

---

## 🎬 Suggested Priority

### **Must-Have (Phase 1)**

1. Basic wallet CRUD
2. Mock deposit
3. Deposit lock/unlock for bidding
4. Winner payment flow
5. Forfeit logic

### **Should-Have (Phase 1.5)**

6. Transaction history with filters
7. Platform fee calculation
8. Basic admin dashboard

### **Nice-to-Have (Phase 2)**

9. VNPay integration
10. Real withdrawal
11. Statement generation
12. Advanced security (2FA)

---

## 💬 Bạn nghĩ sao?

Trước khi viết user stories, cần confirm:

- Logic deposit lock có hợp lý không?
- Flow winner payment → seller payout có thiếu bước nào?
- Có cần thêm feature nào cho Phase 1 không?
- Concerns về concurrency/race conditions?

Hãy cho tôi biết ý kiến để refine trước khi breakdown thành issues nhé! 🚀
