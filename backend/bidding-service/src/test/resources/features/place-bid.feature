@bidding @place-bid @regression
Feature: Placing a bid end to end (deposit lock + authoritative apply-bid)

  Background:
    Given user-service knows user "550e8400-e29b-41d4-a716-446655440010" as "Bob"

  @smoke
  Scenario: A first bid locks the deposit, is applied and stored
    Given auction-service has auction "d0000000-0000-0000-0000-000000000001" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service applies bids on auction "d0000000-0000-0000-0000-000000000001" returning price "105.00" and 2 total bids
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000001"
    Then the response status should be 201
    And the response field "data.totalBids" should equal "2"
    And the response field "data.bidId" should be present
    And 1 bid should be stored for auction "d0000000-0000-0000-0000-000000000001"
    And wallet-service should have received 1 deposit-lock request
    And the bid context for auction "d0000000-0000-0000-0000-000000000001" should be cached in Redis

  Scenario: A second bid by the same user skips the wallet
    Given auction-service has auction "d0000000-0000-0000-0000-000000000002" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service applies bids on auction "d0000000-0000-0000-0000-000000000002" returning price "105.00" and 2 total bids
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000002"
    And user "550e8400-e29b-41d4-a716-446655440010" bids "110.00" on auction "d0000000-0000-0000-0000-000000000002"
    Then the response status should be 201
    And 2 bids should be stored for auction "d0000000-0000-0000-0000-000000000002"
    And wallet-service should have received 1 deposit-lock request

  @negative
  Scenario: Insufficient wallet balance rejects the bid and stores nothing
    Given auction-service has auction "d0000000-0000-0000-0000-000000000003" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service rejects deposit locks for insufficient balance
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000003"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_INSUFFICIENT_BALANCE"
    And the response field "errors.required" should equal "20.00"
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000003"
    And auction-service should have received 0 apply-bid requests for auction "d0000000-0000-0000-0000-000000000003"

  @negative
  Scenario: wallet-service failure makes bidding unavailable
    Given auction-service has auction "d0000000-0000-0000-0000-000000000004" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service responds 500 to deposit locks
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000004"
    Then the response status should be 503
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000004"
    And auction-service should have received 0 apply-bid requests for auction "d0000000-0000-0000-0000-000000000004"

  @negative
  Scenario: auction-service rejecting the bid rolls the stored bid back
    Given auction-service has auction "d0000000-0000-0000-0000-000000000005" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service rejects bids on auction "d0000000-0000-0000-0000-000000000005" with 409 "AUCTION_NOT_OPEN"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000005"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000005"

  @negative
  Scenario: auction-service failure during apply-bid rolls back and returns 503
    Given auction-service has auction "d0000000-0000-0000-0000-000000000006" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    And wallet-service locks deposits successfully
    And auction-service rejects bids on auction "d0000000-0000-0000-0000-000000000006" with 500 "INTERNAL_SERVER_ERROR"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "d0000000-0000-0000-0000-000000000006"
    Then the response status should be 503
    And 0 bids should be stored for auction "d0000000-0000-0000-0000-000000000006"
