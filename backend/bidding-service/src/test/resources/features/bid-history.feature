@bidding @history @regression
Feature: Bid history

  Scenario: Anyone can read an auction's bid history, newest first, with one name lookup
    Given bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "100.00" on auction "e0000000-0000-0000-0000-000000000001" 3 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440011" placed a bid of "105.00" on auction "e0000000-0000-0000-0000-000000000001" 2 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "110.00" on auction "e0000000-0000-0000-0000-000000000001" 1 minutes ago
    And user-service resolves user "550e8400-e29b-41d4-a716-446655440010" as "Bob" in batch
    When an anonymous user requests the bid history of auction "e0000000-0000-0000-0000-000000000001"
    Then the response status should be 200
    And the history amounts should be "110.00,105.00,100.00"
    And the response field "data.data[0].bidderName" should equal "Bob"
    And the response field "data.data[1].bidderName" should equal "Unknown bidder"
    And the response field "data.pagination.total" should equal "3"
    And user-service should have received 1 batch summary request

  Scenario: An auction without bids returns an empty page
    When an anonymous user requests the bid history of auction "e0000000-0000-0000-0000-000000000002"
    Then the response status should be 200
    And the response field "data.pagination.total" should equal "0"
    And user-service should have received 0 batch summary requests

  Scenario: My bids returns only the caller's bids
    Given bidder "550e8400-e29b-41d4-a716-446655440010" placed a bid of "100.00" on auction "e0000000-0000-0000-0000-000000000003" 2 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440011" placed a bid of "105.00" on auction "e0000000-0000-0000-0000-000000000003" 1 minutes ago
    And user-service resolves user "550e8400-e29b-41d4-a716-446655440010" as "Bob" in batch
    When user "550e8400-e29b-41d4-a716-446655440010" requests their bids on auction "e0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the history amounts should be "100.00"

  @negative
  Scenario: My bids requires the gateway user header
    When an anonymous user requests their bids on auction "e0000000-0000-0000-0000-000000000003"
    Then the response status should be 401
