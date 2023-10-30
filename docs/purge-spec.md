# Permanently removing resources from Blaze (WIP)

## Key assumptions

- Identifiers will not be purged. It is users' responsibility to avoid leaking sensitive data into identifiers as it is a larger security issue.
- Referential integrity will only be kept for the latest versions of data as keeping up with all versions is not feasible.

## Terminology

- Delete: Marking a resource as deleted, creating a new version. (Not used, just for reference.)
- Redact: In place updating a resource. (See: [Data absent reason](https://fhir-ru.github.io/extension-data-absent-reason.html) extension.)
- Purge: Permanently removing a resource or marking a resource to be purged by garbage collection. (At this level it should not matter which.)
- Purge id: Each purge action should have a unique id.
- Id duplicating links: Any kind of business logic which may cause a resource to have more than one id.

## Supporting actions

- We can forbid updates which remove/change references. (This also helps with sharding.)
- We can create a blacklist for the purged ids and disallow them to be used. This can also help with dangling references.
- ~~We can forbid id duplicating links.~~ Document that this is not our responsibility because we don't even have the correct API for this.

## Cascading Purge in a Transaction

- We start with a list of identifiers to be purged.
- Generate a fresh purge id. (Or before)
- **Optional:** (Not our responsibility!)
  - We block id duplicating links.
  - We expand the given list by adding new identifiers caused by id duplicating links.
- We add the id list to the blacklist so during the purge we do not create more resources.
- For each id in the original list
  - We purge the resource with its history and
  - Look at all the resources referencing it. We have two kinds of references:
    - Purgeable references: These are patient compartments. We purge them with all their history and cascade the purge.
    - Potentially Unpurgeable references: These are essentially other patients. If the resource is in the list of resources to be purged, purge it. If not, we can redact the reference or leave it as is or just update the resource by removing the reference (this would be only allowed form of changing a reference). The choice depends on how much referential integrity we want. A third option is to fail the transaction.
  - Keep a list of ids purged resources together with the purge id generated at the very beginning. This is for later checking the status of purge operations.
  - Add the ids of purged resources to a blacklist. The only difference is that blacklist is kept forever. The list in the previous item may have a retention period.
- After this step we have an updated list of resources to be purged. We can continue recursively. (**TODO:** How to recurse? Investigate reference types.)

## Should Purge be Blocking?

- Scenario: We start with an id list and blacklist the ids in it so that we do no create new resources by mistake. Other operations can continue. However, since we are performing a cascading purge there may be ids to be purged that we discover later. So what should be blacklisted is not clear from te beginning. Blocking solves this but may be too restricting. **Answer:** Purge is a special operation which should be ideally scheduled so it is not a problem to be restrictive. Also, actual purge will be done by GC.

- We can/should implement a dry-run to see the extend of purge to have an estimate on the work needed.

## Open Questions

- The suggested API for purging supports purging a specific version. This messes the history, so I am not sure what to do about it. One way would be to simply redact the data keeping the slot and _not_ cascading the purge. (Ask on FHIR chat?)

## To Keep in Mind

- In the future we may implement global purge before a certain point in time due to space constraints or retention policies. So keep time integrity so this is possible to implement in the future.


## The Operations

### DELETE /:type/:id/_history

#### Current Spec

Remove all versions of the resource except the current version (which if the resource has been deleted, will be an empty placeholder)

#### Our Interpretation of the Current Spec

* the interaction will be called `delete-history`
  * but see "issues Found" we might to convert this into an operation

* the empty placeholder is just a delete history entry
  * this also means that the server will always keep at least this delete history entry. so it will never forget that a resource with the id ever existed

* if the resource was not deleted, the server will return the 
  * current version on GET /:type/:id (read)
  * history with the single current entry on GET /:type/:id/_history (history-instance)
  * current version on GET /:type/:id/_history/:vid (vread) with the vid of the current version

* if the resource was already deleted, the server will return the  
  * a 410 Gone on GET /:type/:id (read) based on the delete history entry which is still there
  * history with the single delete entry on GET /:type/:id/_history (history-instance)
  * a 410 Gone on GET /:type/:id/_history/:vid (vread) with the vid of the last version because it's a delete version entry

#### Questions

* should it be optional to return a 410 on other GET /:type/:id/_history/:vid (vread) interactions?
  * so basically keeping track of all previous version id's
* should it be possible to refuse the operation if the server likes to preserve possible versioned references?
* will it be possible to create a resource with the same ID again?
  * this in only relevant for servers allowing create as update.
  * it is allowed for the delete interaction but there you have at least the whole history preserved

#### Issues Found

* we can't use DELETE on /:type/:id/_history because the interaction is not idempotent and the history resource still exits after the delete. especially is will exist for ever
* so we propose to use POST /:type/:id/$delete-history

### DELETE /:type/:id/_history/:vid

#### Current Spec

Remove the specified version of the resource. It is an error to remove the 'current' version. (Must first perform a regular delete, and can then delete the non-current version. If the desire is to roll-back to a previous version, use a PUT to "roll forward" instead.)
Same status codes as for the original DELETE operation
Will define the `delete-history` and `delete-history-version` to correspond to these

#### Our Interpretation of the Current Spec

* the interaction will be called `delete-history-version`

* as it's not possible to delete the current version of the history all interpretations from above should also hold here

#### Questions

* should it be optional to return a 410 on other GET /:type/:id/_history/:vid (vread) interactions?
  * so basically keeping track of all previous version id's

### POST /Patient/:id/$purge

#### Current Spec

get rid of all current + historical data for a whole Patient compartment

#### Our Interpretation of the Current Spec

* the $purge operation should work like a combination of DELETE /:type/:id and POST /:type/:id/$delete-history for the Patient resource and all resources of it's compartment
  * if the delete interaction if one of the resources in the compartment is not possible because of referential integrity or other business rules the whole operation will fail
    * this will depend on the servers policies
  * the $delete-history operation will work as described above and so at least one delete history entries will remain for every resource deleted

### POST /Group/:id/$purge

#### Current Spec

get rid of all current + historical data for all Patient compartments of all group members

#### Our Interpretation of the Current Spec

* the $purge operation on groups will work like a combination of $purge operations on all of it's members
* the group resource will be purged also
