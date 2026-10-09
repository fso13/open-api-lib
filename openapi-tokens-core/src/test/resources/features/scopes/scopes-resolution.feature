Feature: Scope resolution
  As a product integrating API tokens
  I want token scopes resolved to project authorities
  So that access control matches the configured mode

  Scenario: Identity mode keeps scopes 1:1
    Given scope mode is "identity"
    And a token with scopes "payments:read,payments:write"
    When scopes are resolved for tenant "tenant-a"
    Then the authorities should be "payments:read,payments:write"

  Scenario: Mapped mode expands scopes from config
    Given scope mode is "mapped"
    And config mapping "payment" to "createPayment,readPayment"
    And a token with scopes "payment"
    When scopes are resolved for tenant "tenant-a"
    Then the authorities should be "createPayment,readPayment"

  Scenario: Mapped mode prefers DB mappings over config for the same token scope
    Given scope mode is "mapped"
    And config mapping "payment" to "createPayment"
    And DB mapping "payment" to "refundPayment" for tenant "tenant-a"
    And a token with scopes "payment"
    When scopes are resolved for tenant "tenant-a"
    Then the authorities should be "refundPayment"
