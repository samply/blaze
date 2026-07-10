# Module Validator

This module contains the `blaze.validator.protocols/Validator` protocol and the
`:blaze/validator` spec. A validator forwards a resource to some validation
mechanism before persistence and returns the resource to persist (possibly
tagged as invalid) or an anomaly. The protocol is implemented by the
[extern-validator](../extern-validator) module.
