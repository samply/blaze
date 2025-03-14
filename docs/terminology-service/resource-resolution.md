# Terminology Resource Resolution

In value sets and terminology operations, code systems and value sets are specified by their canonical URL and optional version. In case multiple CodeSystem/ValueSet resources with the same canonical URL exist, Blaze chooses one CodeSystem/ValueSet resource based on the following criteria with descending priority:

* `status` - in the order `active`, `draft`, `retired`, `unknown`, no status at all
* `version` - in [Semantic Versioning][1] order
* `lastUpdated` - later created/updated resources first
* `id` - by lexical order

Ideally there should not exist two terminology resources with the same canonical URl and version. However Blaze doesn't enforce this.

[1]: <https://semver.org>
