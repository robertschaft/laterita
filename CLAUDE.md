This project attempts to create a language that looks and feels mostly like Java but compiles like Rust. As if Rust was invented by Java Devs not by C++ Devs.

Currently the spec in doc is work in progress and needs to mature.

## Documents

- `doc/laterita-spec.md` — normative spec. Sections 1–18 are the Java-compatible surface (expressible as annotated `.java` that `javac` parses). Section 19 (`LAT-*`) is the `.lat` surface: pure syntactic sugar only.
- `doc/laterita-reasoning.md` — why each rule is the way it is.
- `doc/laterita-open-questions.md` — unresolved language-design questions (`OQ-NN`).
- `doc/resolved-questions.md` — registry of closed decisions: rejected alternatives + resolved-OQ tombstones.
- `doc/terminology.md` — defined terms.

## Working rules

- **Before proposing any new mechanism** for concurrency, ownership, error handling, optionality, cleanup, or syntax, check the "Rejected alternatives" table in `doc/resolved-questions.md`. If it is listed there, the decision is closed — do not re-raise it as an open question or re-propose it unless you have new evidence that directly contradicts the recorded reasoning.
- Any new `.lat` form must be pure syntactic sugar with an exact desugaring to the §1–18 surface (LAT-00). A construct that carries its own semantics belongs in §1–18, expressed through annotations or intrinsics (§18) — not in §19.
- Do not change existing OQ numbers or spec codes. For new questions use unused numbers. Relocating a rule may leave a numbered gap; that is fine (stable IDs).

## Reasoning-document voice

`doc/laterita-reasoning.md` must read as if the current spec were the first commit. It states *why the current rule holds*, not the history of how it was reached. Present discarded alternatives as evaluated-and-rejected, not as project history: write "X causes problem Y and is therefore rejected", never "in earlier drafts we did X" or "previously the spec said X". Do not narrate the design conversation.

## Resolving a question

If a question is resolved, document the reasoning in `doc/laterita-reasoning.md`, remove the question from `doc/laterita-open-questions.md`, and add a tombstone to `doc/resolved-questions.md`. If the resolution rejects a named alternative, also add a row to the "Rejected alternatives" table so it is not re-raised.

Non-language-design items (tooling, migration, roadmap) are tracked as GitHub issues on `robertschaft/laterita`, not in the open-questions document.

## Documentation style

Use one line per sentence.
Do not use semicolons or em dashes in prose text (code blocks and section heading formats like `### RULE — Title` are exempt).
