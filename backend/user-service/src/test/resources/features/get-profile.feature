@user @profile @regression
Feature: Get User Profile

  @smoke
  Scenario: Authenticated user retrieves their own profile
    When user with id "550e8400-e29b-41d4-a716-446655440001" requests their profile
    Then the response status should be 200
    And the response field "data.userId" should equal "550e8400-e29b-41d4-a716-446655440001"

  @negative
  Scenario: Non-existent profile returns 404
    When user with id "00000000-0000-0000-0000-000000000099" requests their profile
    Then the response status should be 404

  @negative @security
  Scenario: Request without X-User-Id header returns 401
    When an unauthenticated request is made to get profile
    Then the response status should be 401
