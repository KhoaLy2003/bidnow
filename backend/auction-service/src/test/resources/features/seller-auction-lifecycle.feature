@auction @seller @regression
Feature: Seller Auction Lifecycle

  @smoke
  Scenario: Seller creates a draft auction
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "title": "BDD New Auction",
        "description": "BDD test auction description",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2027-01-01T00:00:00Z",
        "endTime": "2027-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"],
        "status": "DRAFT"
      }
      """
    Then the response status should be 201
    And the response field "data.status" should equal "DRAFT"

  @negative
  Scenario: Seller creates auction with missing title returns 400
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "description": "BDD test auction description",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2027-01-01T00:00:00Z",
        "endTime": "2027-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"]
      }
      """
    Then the response status should be 400

  Scenario: Seller updates a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"title": "Updated BDD Title"}
      """
    Then the response status should be 200
    And the response field "data.title" should equal "Updated BDD Title"

  Scenario: Seller can update a SCHEDULED auction before it starts
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000002" with body:
      """
      {"title": "Updated Scheduled Title"}
      """
    Then the response status should be 200
    And the response field "data.title" should equal "Updated Scheduled Title"

  @negative
  Scenario: Seller cannot update an ACTIVE auction
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"title": "Should Fail"}
      """
    Then the response status should be 400

  Scenario: Seller deletes a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" deletes auction "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 204

  @negative
  Scenario: Seller cannot delete an ACTIVE auction
    When seller "550e8400-e29b-41d4-a716-446655440001" deletes auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 400

  @smoke
  Scenario: Seller publishes a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" publishes auction "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 200

  @negative
  Scenario: Seller cannot publish an auction with a past end time
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "title": "BDD Past End Time Auction",
        "description": "BDD test auction with past end time",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2020-01-01T00:00:00Z",
        "endTime": "2020-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"]
      }
      """
    Then the response status should be 201
    When seller "550e8400-e29b-41d4-a716-446655440001" publishes the last created auction
    Then the response status should be 400

  Scenario: Seller cancels their own auction
    When seller "550e8400-e29b-41d4-a716-446655440001" cancels auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"reason": "BDD test cancellation"}
      """
    Then the response status should be 204

  @security
  Scenario: Non-owner cannot modify another seller's auction
    When seller "550e8400-e29b-41d4-a716-446655440002" updates auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"title": "Unauthorized"}
      """
    Then the response status should be 403

  @security
  Scenario: Unauthenticated request to seller endpoint returns 401
    When an unauthenticated request is made to get my auctions
    Then the response status should be 401

  Scenario: Seller lists their own auctions
    When seller "550e8400-e29b-41d4-a716-446655440001" requests their own auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present
