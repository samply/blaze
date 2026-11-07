(ns blaze.db.permissions
  "Permissions of the caller of a transaction.

  Permissions are effective permissions, stating what the caller may do, not
  who the caller is. They contain no identities or roles, because resolving
  them while indexing a transaction would make a replay of the transaction log
  non-deterministic. Roles can exist at the API boundary as long as they are
  mapped to permissions before the transaction is submitted.

  Permissions are stored in the transaction log together with the transaction,
  so that a replay decides exactly the same. So once a permission value was
  logged, its meaning must never change. Stricter rules need a new permission
  value instead.

  Permissions are a map with the following key:

  * :write-access-control - the set of codes of the CodeSystem
    `https://blaze-server.org/fhir/CodeSystem/AccessControl` the caller may
    write resources labelled with, regardless of whether a resource carries the
    code as tag or as security label

  Absent permissions allow to write no resource labelled with an AccessControl
  code. So a caller that doesn't pass permissions never gets more rights than a
  client.")

(def internal
  "The permissions of internal code, which may write resources labelled with
  any AccessControl code.

  Must only be used in transactions whose resources Blaze generated itself,
  like the installation of Blaze-owned resources at startup. Must never be used
  for a write carrying content from a client request, because that would open
  a hole in the access control.

  A resource labelled internal must be purged instead of deleted. The
  protection of its history depends on the label of its current version, and
  the version a delete creates carries no labels. So after a delete, clients
  could delete the historical versions that are still labelled internal. A
  purge removes the current version and the whole history at once."
  {:write-access-control #{"internal" "read-only"}})

(defn may-write?
  "Returns true if `permissions` allow to write resources labelled with the
  AccessControl `code`.

  The `permissions` can be nil, which allows to write no AccessControl code."
  [permissions code]
  (contains? (:write-access-control permissions) code))

(defn decode
  "Decodes permissions read from a format without sets like CBOR, which returns
  the codes as sequence.

  Leaves `x` unchanged if it isn't decodable, so that a spec check afterwards
  can reject it."
  [x]
  (let [codes (when (map? x) (:write-access-control x))]
    (cond-> x
      (sequential? codes) (assoc :write-access-control (set codes)))))
