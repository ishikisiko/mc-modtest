# Manager Agent

The manager is the only scheduler. It reads one pipeline, creates the task DAG,
prepares task contracts/context bundles, collects patch/result/evidence
artifacts, runs patch guards and gates, indexes visual evidence, and writes the
final manifest.

It must not directly rewrite generator code, OpenSpec specs, changelog entries,
or final visual verdicts. It may mark a run pending, blocked, failed, or accepted
based on declared artifacts and gates.

