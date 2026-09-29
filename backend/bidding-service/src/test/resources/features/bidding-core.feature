@bidding @core @regression
Feature: Bidding service core — schema, security, context cache and pre-validation

  @smoke
  Scenario: Liquibase creates the bids schema
    Then the bids table should exist with its indexes

  @negative
  Scenario: A bid without the gateway user header is rejected
    When an unauthenticated request bids "105.00" on auction "c0000000-0000-0000-0000-000000000001"
    Then the response status should be 401

  Scenario: A valid bid passes pre-validation (placement arrives in BID-102)
    Given auction-service has auction "c0000000-0000-0000-0000-000000000002" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "105.00" on auction "c0000000-0000-0000-0000-000000000002"
    Then the response status should be 501
    And the bid context for auction "c0000000-0000-0000-0000-000000000002" should be cached in Redis

  Scenario: The bid context is fetched once and then served from Redis
    Given auction-service has auction "c0000000-0000-0000-0000-000000000003" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "104.00" on auction "c0000000-0000-0000-0000-000000000003"
    And user "550e8400-e29b-41d4-a716-446655440011" bids "104.00" on auction "c0000000-0000-0000-0000-000000000003"
    Then auction-service should have received 1 bid-context request for auction "c0000000-0000-0000-0000-000000000003"

  @negative
  Scenario: A bid below the minimum increment is rejected
    Given auction-service has auction "c0000000-0000-0000-0000-000000000004" with status "ACTIVE", price "100.00", increment "5.00", 1 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "104.00" on auction "c0000000-0000-0000-0000-000000000004"
    Then the response status should be 400
    And the response field "errorCode" should equal "BID_TOO_LOW"
    And the response field "errors.minimumBid" should equal "105.00"

  @negative
  Scenario: The seller cannot bid on their own auction
    Given auction-service has auction "c0000000-0000-0000-0000-000000000005" with status "ACTIVE", price "100.00", increment "5.00", 0 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440001" bids "500.00" on auction "c0000000-0000-0000-0000-000000000005"
    Then the response status should be 403
    And the response field "errorCode" should equal "BID_OWN_AUCTION"

  @negative
  Scenario: A bid on a non-live auction is rejected
    Given auction-service has auction "c0000000-0000-0000-0000-000000000006" with status "SCHEDULED", price "100.00", increment "5.00", 0 bids and seller "550e8400-e29b-41d4-a716-446655440001"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000006"
    Then the response status should be 409
    And the response field "errorCode" should equal "AUCTION_NOT_OPEN"

  @negative
  Scenario: A bid on an unknown auction is rejected
    Given auction-service responds 404 for the bid context of auction "c0000000-0000-0000-0000-000000000007"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000007"
    Then the response status should be 404
    And the response field "errorCode" should equal "AUCTION_NOT_FOUND"

  @negative
  Scenario: auction-service failure makes bidding unavailable
    Given auction-service responds 500 for the bid context of auction "c0000000-0000-0000-0000-000000000008"
    When user "550e8400-e29b-41d4-a716-446655440010" bids "100.00" on auction "c0000000-0000-0000-0000-000000000008"
    Then the response status should be 503
    And the response field "errorCode" should equal "SERVICE_UNAVAILABLE"
