@auction @internal-bid @regression
Feature: Internal bid API used by bidding-service

  @smoke
  Scenario: Bid context of an open auction
    When bidding-service requests the bid context for auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.status" should equal "ACTIVE"
    And the response field "data.totalBids" should equal "0"
    And the response field "data.title" should equal "BDD Bid Open Auction"

  @negative
  Scenario: Bid context of an unknown auction
    When bidding-service requests the bid context for auction "b0000000-0000-0000-0000-0000000000ff"
    Then the response status should be 404
    And the response field "errorCode" should equal "AUCTION_NOT_FOUND"

  Scenario: First bid at the starting price, then increment enforcement
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "500.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.extended" should equal "false"
    And the response field "data.totalBids" should equal "1"
    When bidder "550e8400-e29b-41d4-a716-446655440011" bids "540.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 400
    And the response field "errorCode" should equal "BID_TOO_LOW"
    And the response field "errors.minimumBid" should equal "550.00"
    When bidder "550e8400-e29b-41d4-a716-446655440011" bids "550.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 200
    And the response field "data.previousWinnerId" should equal "550e8400-e29b-41d4-a716-446655440010"
    And the response field "data.currentWinnerId" should equal "550e8400-e29b-41d4-a716-446655440011"

  @negative
  Scenario: Seller cannot bid on their own auction
    When bidder "550e8400-e29b-41d4-a716-446655440001" bids "99999.00" on auction "b0000000-0000-0000-0000-000000000005"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_OWN_AUCTION"

  @negative
  Scenario: Bid after the end time is rejected
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "500.00" on auction "b0000000-0000-0000-0000-000000000006"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"

  @concurrency
  Scenario: Concurrent bids at the same price — exactly one is accepted
    When 5 bidders concurrently bid "1010.00" on auction "b0000000-0000-0000-0000-000000000007"
    Then exactly 1 of the concurrent bids should succeed
    And the remaining concurrent bids should be rejected with status 400
    And auction "b0000000-0000-0000-0000-000000000007" should have 2 total bids and current price "1010.00"


  @concurrency
  Scenario: Closure racing concurrent bids never loses the winner
    When the close race runs over auctions "b0000000-0000-0000-0000-000000000008,b0000000-0000-0000-0000-00000000000a,b0000000-0000-0000-0000-00000000000b,b0000000-0000-0000-0000-00000000000c" with 6 bidders each
    Then every raced auction should be COMPLETED with the highest accepted bidder as winner
    And every raced auction total bids should equal 1 plus its accepted bids and rejections should be 400 or 409
    And at least one raced bid should have been accepted

  @concurrency @negative
  Scenario: Bid waiting on a held row lock fails fast instead of hanging
    Given the row lock on auction "b0000000-0000-0000-0000-000000000009" is held by another transaction for 3000 ms
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "500.00" on auction "b0000000-0000-0000-0000-000000000009"
    Then the bid response should be a server error returned within 2500 ms

  @anti-snipe
  Scenario: A bid inside the final two minutes extends the auction by five minutes
    Given auction "b0000000-0000-0000-0000-00000000000d" ends in 60 seconds
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "b0000000-0000-0000-0000-00000000000d"
    Then the response status should be 200
    And the response field "data.extended" should equal "true"
    And the response field "data.extensionCount" should equal "1"
    And auction "b0000000-0000-0000-0000-00000000000d" should end about 360 seconds from now
    And auction "b0000000-0000-0000-0000-00000000000d" should have 1 extension recorded

  @anti-snipe
  Scenario: The closure job for the original end time does not close an extended auction
    Given auction "b0000000-0000-0000-0000-00000000000e" ends in 2 seconds
    When bidder "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "b0000000-0000-0000-0000-00000000000e"
    Then the response status should be 200
    And the response field "data.extended" should equal "true"
    When 3 seconds pass
    And the closure job runs for auction "b0000000-0000-0000-0000-00000000000e"
    Then auction "b0000000-0000-0000-0000-00000000000e" should still be ACTIVE
    And auction "b0000000-0000-0000-0000-00000000000e" should end about 298 seconds from now
