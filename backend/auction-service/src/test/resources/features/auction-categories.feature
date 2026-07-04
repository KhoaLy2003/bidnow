@auction @categories @regression
Feature: Auction Categories

  @smoke
  Scenario: Any user can retrieve active auction categories
    When a request is made to get all auction categories
    Then the response status should be 200
    And the response field "data" should be present
