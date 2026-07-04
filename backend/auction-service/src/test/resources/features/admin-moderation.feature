@auction @admin @regression
Feature: Admin Auction Moderation

  @smoke
  Scenario: Admin lists all auctions
    When an admin lists all auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present

  Scenario: Admin gets auction detail with status history
    When an admin gets auction detail for "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 200
    And the response field "data.id" should equal "b0000000-0000-0000-0000-000000000001"

  Scenario: Admin rejects a SCHEDULED auction
    When an admin rejects auction "b0000000-0000-0000-0000-000000000002" with body:
      """
      {"reason": "Policy violation"}
      """
    Then the response status should be 200
    And the response field "data.status" should equal "REJECTED"

  @negative
  Scenario: Admin cannot reject a non-SCHEDULED auction
    When an admin rejects auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"reason": "Wrong status"}
      """
    Then the response status should be 400

  Scenario: Admin cancels an ACTIVE auction
    When an admin cancels auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"reason": "Policy violation"}
      """
    Then the response status should be 200
    And the response field "data.status" should equal "CANCELLED"

  Scenario: Admin force-closes an ACTIVE auction with bids
    When an admin force-closes auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the response field "data.status" should equal "COMPLETED"

  @security
  Scenario: Non-admin user cannot access admin endpoints
    When a non-admin user lists all auctions
    Then the response status should be 403
