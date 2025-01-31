# Batch

The batch interaction allows to submit a set of actions to be performed as a single HTTP request. The semantics of the individual actions described in the `batch` Bundle are identical of the semantics of the corresponding individual request. The actions are performed in order but can be interleaved by other individual requests or actions from other batch interactions.

```
POST [base]
```
