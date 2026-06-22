# Security policy

## Reporting

Do not disclose vulnerabilities, credentials, or personal data in a public
issue. Report security concerns privately through the contact options on the
repository owner's GitHub profile.

Include the affected endpoint or component, reproduction steps, impact, and a
suggested remediation when possible. Do not test against production accounts
or retain user data.

## Repository safeguards

- Production secrets are externalized and must never be committed.
- `.env` and local deployment configuration remain untracked.
- Tests and documentation must use synthetic identities and placeholder data.
- Authorization, rate-limit, payment, and dispatch issues are treated as
  security-sensitive.
