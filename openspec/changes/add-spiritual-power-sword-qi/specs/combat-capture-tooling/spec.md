## ADDED Requirements

### Requirement: Sword-qi evidence uses real input and measured sessions
The sword-qi acceptance workflow SHALL avoid DevBridge and drive the Minecraft client through mapped press/release input for action evidence. RCON MAY prepare fixtures and read server results, but direct server invocation and pose probes SHALL NOT substitute for input evidence. Each measured run SHALL record UTC start/end, elapsed seconds, command, exit status, environment/cache context, available CPU time and peak RSS, and retain failed attempts and reruns. Setup, build, server readiness and client work SHALL remain distinguishable.

#### Scenario: A mapped release is verified
- **WHEN** the client releases use after a recorded charge
- **THEN** evidence SHALL correlate the input with server action/cost/hit output and label owner visual or feel judgment separately

### Requirement: Capture sessions expose bounded language and restart options
Capture sessions SHALL default to `en_us` and support explicit `zh_cn` for bilingual HUD evidence. A reuse-world option SHALL preserve only the isolated capture world; default startup SHALL retain its fresh-world behavior. Reuse SHALL fail before launch if the capture world is missing. Documentation SHALL require clean stage-1 preparation before capture persistence checks because both use `run-acceptance`; preserving the profile with `--no-clean` SHALL NOT count as the clean acceptance baseline. The journey mode SHALL exercise mapped dialogue, borrowing, manual study and use without granting membership, a manual or learned technique by command; an eligible-prospect setup SHALL be labelled as a fixture. HUD captures SHALL cover the three documented GUI sizes with idle and charge states.

#### Scenario: Chinese HUD is captured after a saved session
- **WHEN** a new session starts with the isolated world reuse option and explicit `zh_cn`
- **THEN** it SHALL retain that capture world, select Chinese, and leave unrelated worlds untouched

#### Scenario: Journey evidence begins from a fixture
- **WHEN** the journey prepares an eligible prospect before mapped joining and study
- **THEN** its manifest SHALL state that preparation and SHALL NOT claim a complete from-new-character progression run

#### Scenario: A preparation step removed the saved capture world
- **WHEN** reuse is requested after clean stage-1 preparation deleted the shared profile
- **THEN** capture SHALL fail with a missing-world diagnostic before starting the server
- **AND** evidence SHALL NOT present the new world's defaults as a product persistence failure or successful same-world restart
