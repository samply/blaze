# Design Documents

This directory holds the design documents of Blaze. A design document describes a feature that involves real design decisions before it is implemented: the problem it solves, how it works, the alternatives considered and why the chosen design wins. It's reviewed and approved before the implementation starts and committed together with it.

Bug fixes and small enhancements need no design document. The GitHub issue is enough.

## Format

Design documents follow the structure of [Rust RFCs](https://github.com/rust-lang/rfcs/blob/master/0000-template.md). Start a new one by copying [`0000-template.md`](0000-template.md) to `YYYY-MM-DD-<topic>-design.md`, using the start date and a short topic in kebab case, e.g. `2026-11-19-openvex-design.md`.

The sections are, in this order:

| Section                       | Content                                                                    |
|-------------------------------|----------------------------------------------------------------------------|
| Summary                       | One paragraph explaining the design.                                       |
| Motivation                    | The problem, with Goals and Non-Goals as subsections.                      |
| Guide-Level Explanation       | The design as seen by clients, operators or contributors, by example.      |
| Reference-Level Explanation   | The technical design, its interactions and corner cases.                   |
| Drawbacks                     | Why we should not do this.                                                 |
| Rationale and Alternatives    | The approaches considered, the chosen one and further decisions.           |
| Prior Art                     | How others solve the problem, and what standards say about it.             |
| Unresolved Questions          | Open points, and what is left to the implementation.                       |
| Future Possibilities          | Natural extensions and related ideas out of scope.                         |
| Verification                  | How to confirm that the implementation meets the goals.                    |

Every section is present. A section with nothing to say holds "None." instead of being left out, so a reader knows it wasn't forgotten.

## Status

Every design document has a status in its header:

* **Proposed:** the design is under review. A document is only proposed on a branch, never on `main`.
* **Accepted:** the design was approved. It's set before the change is merged.
* **Superseded:** a later design replaces this one, as a whole or in parts. A `Superseded By` line in the header links the later design and names the replaced sections, if it's only some of them.

An accepted design document is a record of the decision, not a description of the current code, so it isn't updated when the code changes later. Only its header changes, when it gets superseded. A change that alters an accepted design gets a design document of its own, which says what it replaces. Errors, like a wrong name or a broken link, may still be fixed.

## Divergences from Rust RFCs

* **Location and naming:** a design document is a file `YYYY-MM-DD-<topic>-design.md` in this directory, not a numbered file in a separate repository. The date orders the documents, so no number has to be assigned.
* **Process:** there is no separate RFC pull request. The design is reviewed in its GitHub issue or in the pull request of the implementation, and committed together with the implementation.
* **Header:** instead of the feature name, RFC PR and tracking issue, the header holds the start date, the status and the issues the design was written for. A design can be written for several issues, and later issues can build on it. These later issues link the design, so the design doesn't have to list them.
* **Goals and Non-Goals:** the Motivation section has the subsections Goals and Non-Goals, which make the scope explicit and checkable.
* **Verification:** a section Rust RFCs don't have, naming the checks beyond unit tests that confirm the implementation meets the goals.
* **Headings:** headings are written in title case, like the rest of the Blaze documentation, and carry no link reference definitions like `[summary]: #summary`.
* **No implementation plans:** the reference-level explanation names the rules the code has to follow, not the steps to write it. Implementation plans go stale once the code exists, so they're never committed.
