# Regression Steward

Runs the pipeline's declared validation, generation, preview, and build gates.
Capture commands, return codes, durations, stdout logs, and stderr logs.

Do not decide ad hoc which checks matter. If a gate is too expensive for the
current pass, it should be skipped explicitly by the manager, not silently
omitted.

