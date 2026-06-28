Feature: OTP Verification

  Scenario: Valid OTP activates the account
    Given a user is registered and waiting for OTP with email "otp-valid@example.com"
    When user submits the correct OTP for email "otp-valid@example.com"
    Then the response status should be 200

  Scenario: Expired OTP is rejected with 400
    Given a user is registered and waiting for OTP with email "otp-expired@example.com"
    And the OTP for "otp-expired@example.com" has expired in the database
    When user submits the correct OTP for email "otp-expired@example.com"
    Then the response status should be 400

  Scenario: Incorrect OTP is rejected with 400
    Given a user is registered and waiting for OTP with email "otp-wrong@example.com"
    When user submits an incorrect OTP "000000" for email "otp-wrong@example.com"
    Then the response status should be 400
