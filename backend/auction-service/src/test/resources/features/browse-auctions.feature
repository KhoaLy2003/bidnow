@auction @browse @regression
Feature: Browse Auctions

  @smoke
  Scenario: Public user browses auctions with no filters
    When a public request is made to browse auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present

  Scenario: Public user browses auctions with filters
    When a public request is made to browse auctions with params "minPrice=50&maxPrice=500&page=0&size=5"
    Then the response status should be 200

  @negative
  Scenario: Browse request with size exceeding cap returns 400
    When a public request is made to browse auctions with params "size=200"
    Then the response status should be 400

  Scenario: Public user gets a known active auction by ID
    When a public request is made to get auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the response field "data.title" should equal "BDD Active Auction"

  @negative
  Scenario: Public user gets an unknown auction returns 404
    When a public request is made to get auction "00000000-0000-0000-0000-000000000099"
    Then the response status should be 404

  Scenario: Public user gets category auction counts
    When a public request is made to get category auction counts
    Then the response status should be 200
    And the response field "data" should be present
