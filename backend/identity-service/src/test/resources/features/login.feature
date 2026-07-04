@identity @regression
Feature: User Login

  @smoke
  Scenario: Successful login returns access token
    When user logs in with email "seed-verified@bddtest.local" and password "P@ssw0rd1"
    Then the response status should be 200
    And the response field "data.accessToken" should be present

  @negative @security
  Scenario: Wrong password is rejected with 401
    When user logs in with email "seed-verified@bddtest.local" and password "WrongPassword"
    Then the response status should be 401

  @negative @security
  Scenario: Unverified account cannot login and returns 401
    When user logs in with email "seed-unverified@bddtest.local" and password "P@ssw0rd1"
    Then the response status should be 401
