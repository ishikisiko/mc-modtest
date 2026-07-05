## 1. State Store Implementation

- [x] 1.1 Add `tools/genops/state_store.py` with `init`, `rebuild`,
  `index-run`, `current`, `pending-decisions`, `closeout-ready`,
  `record-decision`, and `artifact-owner` commands.
- [x] 1.2 Store the database under `.genops/state.sqlite` by default and enable
  SQLite WAL/busy-timeout pragmas.
- [x] 1.3 Rebuild indexes from `reports/agent_runs/**` manifests/task evidence,
  OpenSpec active changes, and archive directories.
- [x] 1.4 Mirror recorded decisions to run evidence when a run id is supplied.

## 2. Documentation and Contracts

- [x] 2.1 Add GenOps delta spec requirements for the rebuildable operational
  index, continuity queries, decision recovery, and closeout gate awareness.
- [x] 2.2 Update `CRAFT.md`, `docs/ai-kb/19_genops.md`, and `genops/README.md`
  with the state-store role and commands.
- [x] 2.3 Add `.genops/` to `.gitignore`.

## 3. Verification

- [x] 3.1 Add a smoke test for rebuilding run/task/artifact indexes.
- [x] 3.2 Run the state-store smoke test and Python compile check.
- [x] 3.3 Run `state_store.py rebuild` against the current repository.
- [x] 3.4 Validate the OpenSpec change strictly.
- [x] 3.5 Run `./gradlew build`.
