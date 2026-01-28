<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const tag = release.substring(1);
</script>

# Database Migration

If Blaze starts up with the following error message:

```
Incompatible index store version <x> found. This version of Blaze needs
version <y>.

Either use an older version of Blaze which is compatible with index store
version <x> or do a database migration described here:

  https://github.com/samply/blaze/tree/master/docs/database/migration.md
```

you need to do a migration of the index store.

Blaze will do that migration automatically at startup if you delete the index store. So all you need to do is to make a
backup of all the data Blaze has written to disk, **plan for a downtime**, delete the index store and start Blaze normally.

## Deleting the Index Store

Please start Blaze with a shell assuming that you use the volume `blaze-data`:

```sh-vue
docker run -it -v blaze-data:/app/data samply/blaze:{{ tag }}@{{ digest }} sh
```

in that shell, go into `/app/data` and list all directories:

```sh
cd /app/data
ls
```

you should see the three directories `index`,  `resource` and `transaction`.

If you have enough disk space, just rename the index directory into `index-old`. If not, delete it assuming you have a
backup!

Exit the shell und start Blaze normally.

## Index Store Migration at Start

If you start Blaze without an index store, it will use the transaction log and the resource store to recreate the index
store. During that process Blaze will not be available for reads and writes. Reading from Blaze will result in
a `503 Service Unavailable` with the following `OperationOutcome`:

```json
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "error",
      "code": "timeout",
      "diagnostics": "Timeout while trying to acquire the latest known database state. At least one known transaction hasn't been completed yet. Please try to lower the transaction load or increase the timeout of 10,000 ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often."
    }
  ]
}
```

The time needed for the rebuild of the index store is roughly the time all transactions ever happened in this instance of Blaze took.
