Feature: Create API token
  As an authenticated owner
  I want to create an API token
  So that I can automate access to the API

  Scenario: Successfully create token
    Given an owner "owner-1" with 0 existing tokens
    And the max tokens per owner is 10
    When the owner creates a token named "ci" with scopes "read,write"
    Then the raw token should be returned once
    And the stored token should contain a hash not the secret
    And the token status should be "ACTIVE"

  Scenario: Reject when quota exceeded
    Given an owner "owner-1" with 10 existing tokens
    And the max tokens per owner is 10
    When the owner creates a token named "ci" with scopes "read"
    Then token creation should fail with quota exceeded
