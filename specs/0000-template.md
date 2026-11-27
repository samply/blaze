# Title of the Design

- Start Date: YYYY-MM-DD
- Status: Proposed
- Issues: #0000

## Summary

One paragraph explaining the design.

## Motivation

Why are we doing this? Describe the problem Blaze has today, with the background needed to understand it, and the use cases the design solves. This section is one of the most important ones and can be lengthy.

### Goals

* What the design has to achieve. Each goal should be specific enough to check whether the design meets it.

### Non-Goals

* What the design deliberately doesn't do, even though a reader might expect it. Say why, if it isn't obvious, and whether it can be added later.

## Guide-Level Explanation

Explain the design as if it were already part of Blaze, from the point of view of the people it affects: clients of the FHIR API, operators deploying Blaze or contributors working on the code. That generally means:

* Introducing new concepts and naming them.
* Explaining the design mainly through examples, like requests and responses, configuration or CLI calls.
* Showing error messages, warnings or migration steps, if applicable.

For a change that is purely internal, explain how contributors should think about it and give examples of its concrete impact.

## Reference-Level Explanation

The technical part of the design. Explain it in enough detail that:

* Its interaction with other parts of Blaze is clear.
* It is reasonably clear how it would be implemented.
* Corner cases are dissected by example.

Return to the examples of the guide-level explanation and explain how the design makes them work. Use subsections as needed. Don't write an implementation plan: name the rules the code has to follow, not the steps to write it.

## Drawbacks

Why should we *not* do this? Name the costs and trade-offs of the design, like complexity, performance, compatibility or operational burden.

## Rationale and Alternatives

* Why is this design the best of the possible designs?
* What other designs were considered, and why weren't they chosen?
* What is the impact of not doing this?

List the approaches considered, mark the chosen one and give the reasons for the choice. Record further decisions that aren't obvious from the reference-level explanation, together with their reasons.

## Prior Art

Discuss prior art, both the good and the bad: how other FHIR servers, databases or tools solve the problem, what the FHIR specification or other standards say about it, and published papers or posts on it. Write "None." if there is none.

## Unresolved Questions

* What has to be resolved before the design is accepted?
* What is left to the implementation, and what decides it?
* What related issues are out of scope and could be addressed independently later?

Write "None." if there are none.

## Future Possibilities

What are natural extensions of the design, and how would they fit in? This is also a good place for related ideas that are out of scope. Having something written down here is no reason to accept it later. Write "None." if there are none.

## Verification

How do we confirm that the implementation meets the goals? Name the checks beyond the usual unit tests, like integration tests, CI jobs, load tests or manual checks, and what they should show.
