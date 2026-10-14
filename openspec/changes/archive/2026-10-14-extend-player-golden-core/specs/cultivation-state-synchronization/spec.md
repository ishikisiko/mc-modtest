## MODIFIED Requirements

### Requirement: H presents requirements and the release ceiling without control
The read-only H screen SHALL resolve and display current progress/cap,
advancement kind, duration, stability requirement/cost, current transient state,
and unsupported Golden-Core-late release ceiling when data is available. Missing registry
or status data SHALL render an unavailable marker. Opening or rendering H SHALL
send no start, stop, target, or profile payload.

#### Scenario: A full Qi-III profile is displayed
- **WHEN** the registered bottleneck rule and latest profile are available
- **THEN** H SHALL show the 200-tick bottleneck, 650 stability requirement, and half-retention result; its compatibility success cost SHALL be 325

#### Scenario: Golden Core late is displayed
- **WHEN** the current stage has no cap or advancement rule
- **THEN** H SHALL show the current release limit rather than imply a hidden random requirement

#### Scenario: A higher-stage rule is synchronized
- **WHEN** H displays a Qi-IV through IX, Foundation, or Golden-Core-early/middle source with synchronized registry and session data
- **THEN** it SHALL show that stage's authored cap, derived stability requirement and next-stage rule rather than the former Qi-IV limit
- **AND** the existing four input intents SHALL retain their payload shape and server ownership
