This project attempts to create a language that looks and feels mostly like Java but compiles like Rust. As if Rust was invented by Java Devs not by C++ Devs.

Currently the spec in doc is work in progress and needs to mature.

## Documents

- `doc/laterita-spec.md` — normative spec. Topics other than `LAT` are the Java-compatible surface (expressible as annotated `.java` that `javac` parses). The `LAT` topic (`LAT-*`) is the `.lat` surface: pure syntactic sugar only.
- `doc/laterita-reasoning.md` — why each rule is the way it is.
- `doc/laterita-open-questions.md` — unresolved language-design questions (`OQ-NN`).
- `doc/resolved-questions.md` — registry of closed decisions: rejected alternatives + resolved-OQ tombstones.
- `doc/terminology.md` — defined terms.

## Working rules

- **Before proposing any new mechanism** for concurrency, ownership, error handling, optionality, cleanup, or syntax, check the "Rejected alternatives" table in `doc/resolved-questions.md`. If it is listed there, the decision is closed — do not re-raise it as an open question or re-propose it unless you have new evidence that directly contradicts the recorded reasoning.
- Any new `.lat` form must be pure syntactic sugar with an exact desugaring to the Java-compatible surface (LAT-00). A construct that carries its own semantics belongs on the Java-compatible surface, expressed through annotations or intrinsics, not in the `LAT` topic.
- State each fact once in `doc/laterita-spec.md` and reach it from elsewhere by a cross-reference (`per OWN-15`, `(UNS-02)`). Restating a cited rule is a defect, since only one copy gets amended later. The `spec-compactness-reviewer` agent reports spans that repeat information the spec already carries.
- Do not change existing OQ numbers or spec codes. For new questions use unused numbers. Relocating a rule may leave a numbered gap; that is fine (stable IDs).
- A spec code carries no suffix letter. A rule split in two takes two numbers, never `NN-07a` and `NN-07b`.
- Within a topic, rules are grouped by concept and each group starts at the next multiple of ten: `NN-01` upward, then `NN-10`, then `NN-20`. Document order runs from the most basic rule to the highest concept, so a reader meets every rule after the rules it depends on. Gaps are expected rather than a defect.
- A new rule is written where it belongs in its group and takes that group's first free number, so a number may sit out of order. The convention governs a topic being renumbered or newly written, not one that is already stable.

## Specification voice

`doc/laterita-spec.md` states rules, not arguments for them.
It carries no rationale ("because...", "so that...", "this keeps X small", "deliberately") and no history ("previously", "the old rule", "we decided").
Stating what a rule entails is normative and belongs in the spec, while stating why the rule is right belongs in `doc/laterita-reasoning.md`.
The `spec-reasoning-auditor` agent checks a spec change against this rule and reports violations.

## Reasoning-document voice

`doc/laterita-reasoning.md` must read as if the current spec were the first commit. It states *why the current rule holds*, not the history of how it was reached. Present discarded alternatives as evaluated-and-rejected, not as project history: write "X causes problem Y and is therefore rejected", never "in earlier drafts we did X" or "previously the spec said X". Do not narrate the design conversation.

## Resolving a question

If a question is resolved, document the reasoning in `doc/laterita-reasoning.md`, remove the question from `doc/laterita-open-questions.md`, and add a tombstone to `doc/resolved-questions.md`. If the resolution rejects a named alternative, also add a row to the "Rejected alternatives" table so it is not re-raised.

Non-language-design items (tooling, migration, roadmap) are tracked as GitHub issues on `robertschaft/laterita`, not in the open-questions document.

## Documentation style

Use one line per sentence.
Do not use semicolons or em dashes in prose text (code blocks and section heading formats like `### RULE — Title` are exempt).
Apply both rules to every line a PR modifies, even a single-character change, and leave untouched lines as they are.

## Source style

Applies to `.java` and `.lat` sources (COMP-06).

Lines are at most 80 characters, the OpenJDK limit.
Comment prose follows the documentation style above, so a sentence starts on its own line and is wrapped only where it exceeds the line limit.
The semicolon and em-dash rule reaches comment prose only, since a semicolon in code is a statement terminator.
Commented-out code is code rather than prose, and keeps its semicolons.
The OpenJDK file headers are exempt from the prose rules, though the line limit still covers the lines the fork adds to them.
