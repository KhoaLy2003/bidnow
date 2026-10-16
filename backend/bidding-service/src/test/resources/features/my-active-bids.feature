@bidding @my-bids @regression
Feature: My active bids

  Background:
    Given user "550e8400-e29b-41d4-a716-446655440020" has no recorded bids

  Scenario: Active auctions I bid on are listed soonest-ending first, flagged winning or outbid
    Given bidder "550e8400-e29b-41d4-a716-446655440020" placed a bid of "100.00" on auction "e1000000-0000-0000-0000-000000000001" 30 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440020" placed a bid of "120.00" on auction "e1000000-0000-0000-0000-000000000001" 20 minutes ago
    And bidder "550e8400-e29b-41d4-a716-446655440020" placed a bid of "300.00" on auction "e1000000-0000-0000-0000-000000000002" 10 minutes ago
    And auction-service reports these active auctions:
      | auctionId                            | title        | currentPrice | winnerId                             | hoursLeft |
      | e1000000-0000-0000-0000-000000000001 | Leica M3     | 120.00       | 550e8400-e29b-41d4-a716-446655440020 | 30        |
      | e1000000-0000-0000-0000-000000000002 | Omega Watch  | 350.00       | 550e8400-e29b-41d4-a716-446655440099 | 2         |
    When user "550e8400-e29b-41d4-a716-446655440020" requests their active bids
    Then the response status should be 200
    And the active bid auctions should be "e1000000-0000-0000-0000-000000000002,e1000000-0000-0000-0000-000000000001"
    And the response field "data.items[0].isWinning" should equal "false"
    And the response field "data.items[0].myHighestBid" should equal "300.0"
    And the response field "data.items[1].isWinning" should equal "true"
    And the response field "data.items[1].myHighestBid" should equal "120.0"
    And the response field "data.counts.all" should equal "2"
    And the response field "data.counts.winning" should equal "1"
    And the response field "data.counts.outbid" should equal "1"

  Scenario: Auctions that are no longer active are not listed
    Given bidder "550e8400-e29b-41d4-a716-446655440020" placed a bid of "100.00" on auction "e1000000-0000-0000-0000-000000000003" 30 minutes ago
    And auction-service reports these active auctions:
      | auctionId | title | currentPrice | winnerId | hoursLeft |
    When user "550e8400-e29b-41d4-a716-446655440020" requests their active bids
    Then the response status should be 200
    And the response field "data.counts.all" should equal "0"

  Scenario: A user who never bid gets an empty list without calling auction-service
    When user "550e8400-e29b-41d4-a716-446655440020" requests their active bids
    Then the response status should be 200
    And the response field "data.counts.all" should equal "0"
    And auction-service should have received 0 bid summary requests

  Scenario: Other bidders' bids are not included
    Given bidder "550e8400-e29b-41d4-a716-446655440021" placed a bid of "100.00" on auction "e1000000-0000-0000-0000-000000000004" 30 minutes ago
    When user "550e8400-e29b-41d4-a716-446655440020" requests their active bids
    Then the response status should be 200
    And the response field "data.counts.all" should equal "0"
    And auction-service should have received 0 bid summary requests

  @negative
  Scenario: Reporting active bids requires the gateway user header
    When an anonymous user requests their active bids
    Then the response status should be 401

  @negative
  Scenario: A downstream auction-service failure is reported as 503
    Given bidder "550e8400-e29b-41d4-a716-446655440020" placed a bid of "100.00" on auction "e1000000-0000-0000-0000-000000000005" 30 minutes ago
    And auction-service fails when asked for bid summaries
    When user "550e8400-e29b-41d4-a716-446655440020" requests their active bids
    Then the response status should be 503
