# Auction Participation Registration Flow

This flow describes the process of a user explicitly registering for an auction and paying the mandatory deposit before being allowed to place any bids.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant App as Frontend
    participant GW as API Gateway
    participant AS as Auction Service
    participant WS as Wallet Service
    participant DB_AS as Auction DB
    participant DB_WS as Wallet DB
    participant MQ as Message Broker
    participant NS as Notification Service

    Note over User, NS: Registration & Deposit Lock Process
    
    User->>App: Clicks "Register to Participate"
    App->>GW: POST /api/v1/auctions/{id}/register
    GW->>AS: Route request
    
    AS->>AS: Validate: User is not Owner
    AS->>AS: Validate: Auction is ACTIVE
    
    AS->>WS: Request Deposit Lock (UserID, AuctionID, DepositAmount)
    WS->>DB_WS: Check balance & Create Lock record
    
    alt Insufficient Funds
        WS-->>AS: return 400 Insufficient Funds
        AS-->>GW: 400 Bad Request
        GW-->>App: Prompt user to top-up wallet
    else Lock Successful
        WS-->>AS: return 200 Success (LockID)
        
        AS->>DB_AS: Create Registration record (UserID, AuctionID, LockID)
        AS->>MQ: Publish USER_REGISTERED_FOR_AUCTION event
        
        AS-->>GW: 200 OK (Registration Successful)
        GW-->>App: Update UI (Unlock Bidding)
        
        par Async Notification
            MQ-->>NS: Consume Registration event
            NS->>App: WebSocket: "Registration confirmed"
            NS->>User: Email: "Registration confirmed for [Auction]"
        end
    end
```

### Key Business Rules:
1. **Mandatory Deposit:** Users cannot bid without a successful registration and locked deposit.
2. **One-time Registration:** Users only register once per auction.
3. **Refund Policy:** Deposits are automatically released if the user loses the auction (handled in Auction Closure flow).
4. **Forfeiture:** If the winner fails to pay, this locked deposit is forfeited (handled in Winner Payment flow).
