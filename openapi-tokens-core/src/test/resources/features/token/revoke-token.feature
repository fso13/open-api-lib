Feature: Revoke API token
  As an authenticated owner
  I want to revoke my API token
  So that it can no longer be used

  Scenario: Successfully revoke token
    Given an owner "owner-1" owns an active token
    When the owner revokes the token
    Then the token status should be "REVOKED"
