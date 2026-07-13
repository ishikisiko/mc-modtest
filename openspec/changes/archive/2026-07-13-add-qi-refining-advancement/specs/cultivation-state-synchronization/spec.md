## ADDED Requirements

### Requirement: Advancement status is server-derived and clientbound-only
The server SHALL report accepted ordinary/bottleneck state, declared duration,
interruption/rejection reason, and completion result through the existing
clientbound runtime-status architecture. Status SHALL be sent at transitions,
not every tick, and SHALL not install or author a client profile. The only new
client input SHALL be the bounded start-breakthrough action added to the same
intent payload.

#### Scenario: Advancement starts
- **WHEN** the server accepts a valid current-stage rule
- **THEN** the owning client SHALL receive status derived from that rule's kind and duration
- **AND** no profile snapshot SHALL be sent merely for starting transient state

#### Scenario: Advancement completes
- **WHEN** one immutable transition profile commits
- **THEN** the owning client SHALL receive one final changed profile snapshot and completion status
- **AND** no target or result SHALL have originated from client data

### Requirement: Client advancement state remains a read-only cache
The client SHALL cache server status only for presentation and SHALL clear it
on disconnect. It SHALL not expose an API to complete a transition, change its
duration, choose a target, deduct stability, or replace a profile.

#### Scenario: The client disconnects during advancement
- **WHEN** client logout fires
- **THEN** cached advancement status SHALL clear before a later connection can observe it

### Requirement: H presents requirements and the release ceiling without control
The read-only H screen SHALL resolve and display current progress/cap,
advancement kind, duration, stability requirement/cost, current transient state,
and unsupported Qi-IV release ceiling when data is available. Missing registry
or status data SHALL render an unavailable marker. Opening or rendering H SHALL
send no start, stop, target, or profile payload.

#### Scenario: A full Qi-III profile is displayed
- **WHEN** the registered bottleneck rule and latest profile are available
- **THEN** H SHALL show the 200-tick bottleneck, 80 stability requirement, and 30 success cost

#### Scenario: Qi IV is displayed
- **WHEN** the current stage has no cap or advancement rule
- **THEN** H SHALL show the current release limit rather than imply a hidden random requirement
