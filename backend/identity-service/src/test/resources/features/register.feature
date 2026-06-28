Feature: User Registration

  Scenario: Successful registration returns 202 with user data
    When user registers with name "Alice" email "alice@example.com" and password "P@ssw0rd1"
    Then the response status should be 202
    And the response field "data.email" should equal "alice@example.com"
    And the response field "data.accountStatus" should equal "PENDING_VERIFICATION"

  Scenario: Duplicate email is rejected with 400
    When user registers with name "Alice" email "alice@bddtest.local" and password "P@ssw0rd1"
    Then the response status should be 400

  Scenario: Invalid email format is rejected with 400
    When user registers with name "Bob" email "not-an-email" and password "P@ssw0rd1"
    Then the response status should be 400

  Scenario: Short password is rejected with 400
    When user registers with name "Bob" email "bob@example.com" and password "short"
    Then the response status should be 400
