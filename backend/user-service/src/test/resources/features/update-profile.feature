@user @profile @regression
Feature: Update User Profile

  @smoke
  Scenario: User updates display name successfully
    When user with id "550e8400-e29b-41d4-a716-446655440002" updates display name to "Bob Updated"
    Then the response status should be 200
    And the response field "data.displayName" should equal "Bob Updated"

  @negative
  Scenario: Display name exceeding 100 characters is rejected
    When user with id "550e8400-e29b-41d4-a716-446655440002" updates display name to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    Then the response status should be 400
