(ns blaze.db.node.tx-indexer.verify
  "Verifies terminal transaction commands. Non-terminal transaction commands
  are first expanded.

  Authorizes each command against the permissions of the transaction (see
  `blaze.db.permissions`). A command touching a resource labelled with an
  AccessControl code is only allowed if the permissions allow to write that
  code:

  * the current version tagged `read-only` can't be updated or deleted
  * the current version labelled `internal` can't be updated or deleted and its
    history can't be deleted or purged, neither at once nor version by version
  * a new version labelled with any AccessControl code can't be created

  Only the current version is checked, because the labels are found through
  index entries, and the index entries of historical versions are removed
  eventually. A deleted resource carries no labels, so its historical versions
  are unprotected. That is why internal code
  has to purge a resource labelled internal instead of deleting it (see
  `blaze.db.permissions/internal`).

  The check of the current version tagged `read-only` stays restricted to
  updates and deletes, because transactions accepted before have to be
  accepted again on replay. For the same reason, commands without extracted
  AccessControl codes of the new version, like ones logged before they were
  extracted, aren't checked."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.fold :as f]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.kv.spec]
   [blaze.db.node.duplicates :as dup]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.permissions :as p]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.util :as u :refer [conj-vec str]]
   [clojure.string :as str]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defhistogram transaction-sizes
  "Number of transaction commands per transaction."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "transaction_sizes"}
  (take 16 (iterate #(* 2 %) 1)))

(defn- duplicate-commands-anom
  "Returns a conflict anomaly if two of `cmds` act on the same resource or nil
  if none do."
  [cmds]
  (when-let [{:keys [cmd existing-cmd]} (dup/find-duplicate cmds)]
    (ba/conflict (dup/duplicate-msg existing-cmd cmd))))

(defmulti verify
  "Verifies `command`. Returns `res` with added index entries and statistics of
  the transaction outcome or a reduced anomaly on conflicts.

  A reducing function of the verification result and the commands, which stops
  the reduction at the first conflict."
  {:arglists '([context t res command])}
  (fn [_context _t _res {:keys [op]}] op))

(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))

(defn- id-collision-msg [type id t]
  (format "Resource `%s/%s` already exists in the database with t = %d and can't be created again." type id t))

(defn- index-entries
  "Creates index entries for the resource with `tid` and `id`.

  `refs` are used to update the PatientLastChange index in case a Patient is
  referenced."
  [tid id t hash num-changes op refs]
  (into
   (rts/index-entries tid id t hash num-changes op)
   (keep (fn [[ref-type ref-id]]
           (when (= "Patient" ref-type)
             (plc/index-entry ref-id t))))
   refs))

(defn- add-version
  "Adds the index entries of the new version of the resource with `tid` and `id`
  and its update of the resource handle cache to `res`."
  [res tid id t hash num-changes op refs]
  (-> (update res :entries into (index-entries tid id t hash num-changes op refs))
      (update :resource-handle-updates conj
              [(rts/resource-handle tid id t hash num-changes op) false])))

(def ^:private inc-0 (fnil inc 0))
(def ^:private dec-0 (fnil dec 0))
(def ^:private minus-0 (fnil - 0))

(defn- inc-num-changes-and-total [stat]
  (-> (update stat :num-changes inc-0)
      (update :total inc-0)))

(defn- labelled-new-version-msg [verb type id code]
  (format "Can't %s the resource `%s/%s` labelled with the AccessControl code `%s`."
          verb type id code))

(defn- labelled-new-version-anom
  "The `verb` names the command in the message."
  [verb type id code]
  (ba/conflict (labelled-new-version-msg verb type id code)))

(defn- first-disallowed-access-control-code
  "Returns the first AccessControl code that the permissions of `context` don't
  allow to write of the AccessControl codes the new version of `tx-cmd` is
  labelled with or nil if they allow to write all of them."
  {:arglists '([context tx-cmd])}
  [{:keys [permissions]} {:keys [access-control]}]
  (first (remove (partial p/may-write? permissions) access-control)))

(defmethod verify "create"
  [{:keys [db-before] :as context} t res {:keys [type id hash refs] :as tx-cmd}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-create")]
    (if (d/resource-handle db-before type id)
      (reduced (ba/conflict (id-collision-msg type id (d/t db-before))))
      (if-let [code (first-disallowed-access-control-code context tx-cmd)]
        (reduced (labelled-new-version-anom "create" type id code))
        (let [tid (codec/tid type)]
          (-> (add-version res tid id t hash 1 :create refs)
              (update :new-resources conj [type id])
              (update-in [:stats tid] inc-num-changes-and-total)))))))

(defmethod verify "hold"
  [_context _t res _tx-cmd]
  res)

(defn- print-etags [ts]
  (str/join "," (map (partial format "W/\"%d\"") ts)))

(defn- verify-tx-cmd-put-msg [type id if-match if-none-match]
  (cond
    if-match
    (format "verify-tx-cmd :put %s/%s if-match: %s" type id (print-etags if-match))
    if-none-match
    (format "verify-tx-cmd :put %s/%s if-none-match: %s" type id if-none-match)
    :else
    (format "verify-tx-cmd :put %s/%s" type id)))

(defn- precondition-failed-msg [if-match type id]
  (format "Precondition `%s` failed on `%s/%s`." (print-etags if-match) type id))

(defn- precondition-failed-anomaly [if-match type id tx-cmd]
  (ba/conflict (precondition-failed-msg if-match type id)
               :http/status 412 :blaze.db/tx-cmd tx-cmd))

(defn- precondition-any-failed-msg [type id]
  (format "Resource `%s/%s` already exists." type id))

(defn- precondition-any-failed-anomaly [type id]
  (ba/conflict (precondition-any-failed-msg type id) :http/status 412))

(defn- precondition-version-failed-msg [type id if-none-match]
  (format "Resource `%s/%s` with version %d already exists." type id if-none-match))

(defn- precondition-version-failed-anomaly [type id if-none-match]
  (ba/conflict (precondition-version-failed-msg type id if-none-match) :http/status 412))

(defn- labelled-current-version-anom
  "Returns the anomaly of a command, named by `verb`, touching the current
  version of the resource with `type` and `id` labelled with the
  AccessControl `code`."
  [verb code type id]
  (ba/conflict (format "Can't %s the %s resource `%s/%s`." verb code type id)))

(defn- types-without-match
  "Returns the set of the types of `tx-cmds` without any resource in
  `db-before`, the database before the transaction, that may match `matcher`.

  Takes one seek per type and clause value, so that the resources of these
  types don't need one each. The types of all commands are sought, whatever
  their op, so that the set doesn't depend on which commands check a label."
  [db-before matcher tx-cmds]
  (into
   #{}
   (comp (map :type)
         (distinct)
         (remove (partial impl/type-may-match? db-before matcher)))
   tx-cmds))

(defn- assoc-label-types
  "Adds the `:types-without-read-only` and `:types-without-internal`, the types
  of `tx-cmds` of which no resource can be tagged read-only or labelled
  internal, to `context`.

  They are delays, determined on the first call of `read-only?` or
  `internal?`, so that a transaction that doesn't change any existing resource
  takes no seeks for them."
  {:arglists '([context tx-cmds])}
  [{:keys [db-before read-only-matcher internal-matcher] :as context} tx-cmds]
  (assoc context
         :types-without-read-only
         (delay (types-without-match db-before read-only-matcher tx-cmds))
         :types-without-internal
         (delay (types-without-match db-before internal-matcher tx-cmds))))

(defn- read-only?
  "Returns true if the `resource-handle` of the current version of a resource
  of `type` is tagged read-only and the permissions of `context` don't allow
  to write such resources.

  The resource handles of the `types-without-read-only` of `context` aren't
  matched, because none of them can be tagged read-only. The ones of every
  other type are, so that a type missing there costs a seek but is never left
  unchecked."
  {:arglists '([context type resource-handle])}
  [{:keys [db-before read-only-matcher types-without-read-only permissions]}
   type resource-handle]
  (and (not (p/may-write? permissions "read-only"))
       (not (contains? @types-without-read-only type))
       (d/matches? db-before read-only-matcher resource-handle)))

(defn- internal?
  "Returns true if the `resource-handle` of the current version of a resource
  of `type` is labelled internal and the permissions of `context` don't allow
  to write such resources.

  The resource handles of the `types-without-internal` of `context` aren't
  matched, because none of them can be labelled internal. The ones of every
  other type are, so that a type missing there costs a seek but is never left
  unchecked."
  {:arglists '([context type resource-handle])}
  [{:keys [db-before internal-matcher types-without-internal permissions]}
   type resource-handle]
  (and (not (p/may-write? permissions "internal"))
       (not (contains? @types-without-internal type))
       (d/matches? db-before internal-matcher resource-handle)))

(defmethod verify "put"
  [{:keys [db-before] :as context} t res
   {:keys [type id hash if-match if-none-match refs] :as tx-cmd}]
  (log/trace (verify-tx-cmd-put-msg type id (u/to-seq if-match) if-none-match))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-put")]
    (let [tid (codec/tid type)
          if-match (u/to-seq if-match)
          {:keys [num-changes op] :or {num-changes 0} old-t :t old-hash :hash
           :as old-resource-handle}
          (d/resource-handle db-before type id)]
      (cond
        (and if-match (not (some #{old-t} if-match)))
        (reduced (precondition-failed-anomaly if-match type id tx-cmd))

        (and (some? old-t) (= "*" if-none-match))
        (reduced (precondition-any-failed-anomaly type id))

        (and (some? old-t) (= if-none-match old-t))
        (reduced (precondition-version-failed-anomaly type id if-none-match))

        ;; identical update, we will do nothing
        (= old-hash hash)
        res

        (some->> old-resource-handle (read-only? context type))
        (reduced (labelled-current-version-anom "update" "read-only" type id))

        (some->> old-resource-handle (internal? context type))
        (reduced (labelled-current-version-anom "update" "internal" type id))

        :else
        (if-let [code (first-disallowed-access-control-code context tx-cmd)]
          (reduced (labelled-new-version-anom "update" type id code))
          (cond->
           (-> (add-version res tid id t hash (inc num-changes) :put refs)
               (update :new-resources conj [type id])
               (update-in [:stats tid :num-changes] inc-0))
            (or (nil? old-t) (identical? :delete op))
            (update-in [:stats tid :total] inc-0)))))))

(defn- verify-tx-cmd-keep-msg [type id if-match]
  (if if-match
    (format "verify-tx-cmd :keep %s/%s if-match: %s" type id (print-etags if-match))
    (format "verify-tx-cmd :keep %s/%s" type id)))

(defmethod verify "keep"
  [{:keys [db-before]} _ res {:keys [type id hash if-match] :as tx-cmd}]
  (log/trace (verify-tx-cmd-keep-msg type id (u/to-seq if-match)))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-keep")]
    (let [if-match (u/to-seq if-match)
          {old-hash :hash old-t :t} (d/resource-handle db-before type id)]
      (cond
        (and if-match (not (some #{old-t} if-match)))
        (reduced (precondition-failed-anomaly if-match type id tx-cmd))

        (not= hash old-hash)
        (let [msg (format "Keep failed on `%s/%s`." type id)]
          (log/trace msg)
          (reduced (ba/conflict msg :blaze.db/tx-cmd tx-cmd)))

        :else
        res))))

(defn- patient-refs
  "Returns references from `resource-handle` to Patient resources."
  [{{:keys [search-param-registry]} :node :as db} type resource-handle]
  (into
   []
   (comp (mapcat #(d/include db resource-handle % "Patient"))
         (map (fn [{:keys [id]}] ["Patient" id])))
   (sr/compartment-resources search-param-registry "Patient" type)))

(defn- mark-delete
  "Marks `type`, the type of a resource whose delete checks references, in
  `multiple-deletes`, a map from the types seen so far to whether more than one
  resource of them is deleted.

  Updates the map only on the first two deleted resources of a type, because no
  further one changes whether there is more than one."
  [multiple-deletes type]
  (case (get multiple-deletes type)
    nil (assoc multiple-deletes type false)
    false (assoc multiple-deletes type true)
    multiple-deletes))

(defn- add-delete
  "Adds the resource deleted or purged by `tx-cmd` to the `:deletes` of `res`,
  a map from the type of each resource to the shards of their ids, maps from
  its id to either its current version, if its delete checks references, or
  true (see `impl/assoc-delete`). The `resource-handle` is the current version
  of the resource or nil if it doesn't exist.

  The ids are nested under their type, so that the check of the references
  looks up the type once for many resources and doesn't need a tuple of type
  and id for each lookup. They are split into shards by their hash, so that the
  deletes of the parts of a large transaction can be merged in parallel (see
  `merged-deletes`).

  Maps the resource to `resource-handle` if `tx-cmd` checks references and the
  resource exists, so that the check of the references doesn't have to look it
  up again. Also marks the type of the resource in the `:multiple-deletes` of
  `res` then, so that whether more than one resource of a type is deleted is
  found out while the commands are verified in parallel and not afterwards on
  the calling thread.

  Otherwise maps the resource to true, unless it was already deleted before,
  because then the transaction doesn't delete it.

  One map for both serves all lookups the check of the references needs, so
  that merging the deletes of the parts of a large transaction touches the key
  of each deleted resource only once."
  {:arglists '([res tx-cmd resource-handle])}
  [res {:keys [type id check-refs]} resource-handle]
  (cond
    (and check-refs resource-handle)
    (-> (update-in res [:deletes type] impl/assoc-delete id resource-handle)
        (update :multiple-deletes mark-delete type))

    (some-> resource-handle rh/deleted?)
    res

    :else
    (update-in res [:deletes type] impl/assoc-delete id true)))

(defmethod verify "delete"
  [{:keys [db-before] :as context} t res {:keys [type id] :as tx-cmd}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0} :as old-resource-handle}
          (d/resource-handle db-before type id)
          refs (some->> old-resource-handle (patient-refs db-before type))
          res (add-delete res tx-cmd old-resource-handle)]
      (cond
        (identical? :delete op) res

        (some->> old-resource-handle (read-only? context type))
        (reduced (labelled-current-version-anom "delete" "read-only" type id))

        (some->> old-resource-handle (internal? context type))
        (reduced (labelled-current-version-anom "delete" "internal" type id))

        :else
        (cond->
         (-> (add-version res tid id t hash/deleted-hash (inc num-changes) :delete refs)
             (update-in [:stats tid :num-changes] inc-0))
          old-resource-handle
          (update-in [:stats tid :total] dec-0))))))

(def ^:private ^:const ^long delete-history-max 100000)

(defn- limited-instance-history
  "Returns a vector of the current version of the resource with `type` and `id`
  followed by at most `delete-history-max` historical versions, so that a
  history exceeding the limit is detected without reading it completely."
  [db type id]
  (into [] (take (inc delete-history-max)) (d/instance-history db type id)))

(defn- too-many-history-entries?
  "Returns true if `instance-history`, returned by `limited-instance-history`,
  has more than `delete-history-max` historical versions."
  [instance-history]
  (= (inc delete-history-max) (count instance-history)))

(defn- too-many-history-entries-msg [type id]
  (format "Deleting the history of `%s/%s` failed because there are more than %,d history entries."
          type id delete-history-max))

(defn- too-many-history-entries-anom [type id]
  (ba/conflict
   (too-many-history-entries-msg type id)
   :fhir/issue "too-costly"))

(defn- purge-entry [tid id t rh]
  (rts/purge-index-entries tid id rh t))

(defn- add-purge-entries
  "Adds the purge index entries of `resource-handles` to `res`.

  Doesn't add updates of the resource handle cache, because it only holds the
  current version of a resource."
  [res tid id t resource-handles]
  (-> (update res :entries into (mapcat (partial purge-entry tid id t)) resource-handles)
      (update-in [:stats tid :num-changes] minus-0 (count resource-handles))))

(defmethod verify "delete-history"
  [{:keys [db-before] :as context} t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete-history" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete-history")]
    (let [[current-handle :as instance-history]
          (limited-instance-history db-before type id)]
      (cond
        (< (count instance-history) 2) res

        (internal? context type current-handle)
        (reduced (labelled-current-version-anom "delete the history of" "internal" type id))

        (too-many-history-entries? instance-history)
        (reduced (too-many-history-entries-anom type id))

        :else
        (add-purge-entries res (codec/tid type) id t (subvec instance-history 1))))))

(defn- current-version-msg [type id t]
  (format "Can't delete the current version `%d` of the resource `%s/%s`."
          t type id))

(defn- current-version? [db type id t]
  (= t (:t (d/resource-handle db type id))))

;; like delete-history but purges only the version with t
(defmethod verify "delete-history-version"
  [{:keys [db-before] :as context} t res {:keys [type id] vid :t}]
  (log/trace "verify-tx-cmd :delete-history-version" (str type "/" id "/_history/" vid))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete-history-version")]
    (if-let [handle (d/historic-resource-handle db-before type id vid)]
      (cond
        (internal? context type (d/resource-handle db-before type id))
        (reduced (labelled-current-version-anom "delete a history version of" "internal" type id))

        (current-version? db-before type id vid)
        (reduced (ba/conflict (current-version-msg type id vid)))

        :else
        (add-purge-entries res (codec/tid type) id t [handle]))
      res)))

;; like delete-history but also purges the current version
(defmethod verify "purge"
  [{:keys [db-before] :as context} t res {:keys [type id] :as tx-cmd}]
  (log/trace "verify-tx-cmd :purge" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-purge")]
    (let [tid (codec/tid type)
          instance-history (limited-instance-history db-before type id)]
      (cond
        (empty? instance-history) res

        (internal? context type (first instance-history))
        (reduced (labelled-current-version-anom "purge" "internal" type id))

        (too-many-history-entries? instance-history)
        (reduced (too-many-history-entries-anom type id))

        :else
        (cond-> (-> (add-purge-entries res tid id t instance-history)
                    (add-delete tx-cmd (first instance-history))
                    ;; the current version is the only one the resource handle
                    ;; cache can hold
                    (update :resource-handle-updates conj
                            [(first instance-history) true]))
          (not (identical? :delete (:op (first instance-history))))
          (update-in [:stats tid :total] dec-0))))))

(def ^:private ^:const ^long part-size
  "The number of commands verified in one part of the parallel verification.

  Small, so that the commands of a large transaction spread over many workers
  and the part with the remaining commands, which the calling thread verifies
  itself, stays small. Combining the results of many parts is cheap, because
  it only collects their entries, resource handle updates and deletes, which
  are merged in parallel afterwards (see `merged-deletes`)."
  128)

(defn- first-anomaly-combiner
  "Returns a function that combines the results of two consecutive parts of
  commands with `combine`, unless one of them is an anomaly. Then it returns the
  anomaly of the earlier part, so that the anomaly is the one of the first
  failing command, as if the commands were reduced one after another.

  Its identity is `init`, the result of no commands, which is combined with the
  result of the first part like the results of all other parts, so that
  `combine` sees every part."
  [init combine]
  (fn
    ([] init)
    ([acc-1 acc-2]
     (cond
       (ba/anomaly? acc-1) acc-1
       (ba/anomaly? acc-2) acc-2
       :else (combine acc-1 acc-2)))))

(defn- fold-tx-cmds
  "Reduces `tx-cmds` with the reducing function `f` starting with `init` like
  `reduce`, but on parts of `n` commands in parallel on `pool`, combining the
  results of consecutive parts with `combine` from left to right (see
  `f/fold`).

  Returns the anomaly of the first command for which `f` returns a reduced
  anomaly instead. Fewer than `n` commands are reduced on the calling thread."
  [pool n init combine f tx-cmds]
  (f/fold pool n #(reduce f init %)
          (first-anomaly-combiner init combine) tx-cmds))

(def ^:private empty-res
  {:entries []
   :resource-handle-updates []
   :new-resources #{}
   :deletes {}
   :part-deletes {}
   :multiple-deletes {}})

(defn- merge-multiple-deletes
  "Merges `multiple-deletes-1` and `multiple-deletes-2`, maps from types to
  whether more than one resource of them is deleted. A type in both maps has
  more than one deleted resource.

  Both maps have at most one entry per type, so that merging them is cheap."
  [multiple-deletes-1 multiple-deletes-2]
  (merge-with (constantly true) multiple-deletes-1 multiple-deletes-2))

(defn- into-unless-empty
  "Returns `to` with `from` added like `into`, or `from` itself if `to` is
  empty, so that the collections of a part aren't copied into the empty ones of
  `empty-res`."
  [to from]
  (if (empty? to) from (into to from)))

(defn- conj-part
  "Adds `part`, the entries or the resource handle updates of a part of the
  commands, to `parts`, unless it's empty, so that no empty part has to be
  stored or applied."
  [parts part]
  (cond-> parts (seq part) (conj part)))

(defn- conj-shards
  "Adds `shards`, the shards of the deleted resources of `type` of a part, to
  the ones of the parts before it in `part-deletes` (see `add-part-deletes`)."
  [part-deletes type shards]
  (update part-deletes type conj-vec shards))

(defn- add-part-deletes
  "Adds `deletes`, the ones of a part (see `add-delete`), to `part-deletes`, a
  map from each type to a vector of the shards of its deleted resources in each
  of the parts before deleting any.

  Takes only a step per type of the part, because the shards are merged only
  after all parts (see `merged-deletes`)."
  [part-deletes deletes]
  (reduce-kv conj-shards part-deletes deletes))

(defn- combine-res
  "Combines `res`, the verification result of the commands so far, with
  `part-res`, the one of the part of the commands following them.

  The results are combined from left to right, so that `res` is the result of
  all parts so far, starting with `empty-res`, and `part-res` the one of a
  single part. That's why `res` holds its `:entries` and its
  `:resource-handle-updates` in vectors of parts, one per part, while
  `part-res` holds the ones of its part. Collecting the parts only adds each
  part and doesn't touch its elements, and the node can store the entries and
  apply the updates of the parts in parallel.

  The `:deletes` of the parts are collected by type in the `:part-deletes` of
  `res` (see `add-part-deletes`), because merging them here would add the ids
  of every part to the ones of the parts before it one after another (see
  `merged-deletes`)."
  [res part-res]
  (-> (update res :entries conj-part (:entries part-res))
      (update :resource-handle-updates conj-part
              (:resource-handle-updates part-res))
      (update :new-resources into-unless-empty (:new-resources part-res))
      (update :part-deletes add-part-deletes (:deletes part-res))
      (update :multiple-deletes merge-multiple-deletes (:multiple-deletes part-res))
      (update :stats (partial merge-with (partial merge-with +)) (:stats part-res))))

(defn- merge-shard
  "Returns a vector of a map from each type of `part-deletes` to its shard with
  the single index of `part`, merged from its shards in the parts.

  The map is in a vector of its own, so that `into` collects the maps of all
  indices, because `into` has the identity `f/fold` needs, unlike `conj`."
  {:arglists '([part-deletes part])}
  [part-deletes [index]]
  [(update-vals part-deletes #(impl/merge-delete-shard % index))])

(defn- type-shards
  "Returns the shards of `type`, one of each of the merged shards of all types,
  `merged-shards`, ordered by index."
  [merged-shards type]
  (mapv #(% type) merged-shards))

(defn- several-parts?
  "Returns true if the entry of `part-deletes` (see `add-part-deletes`) holds
  the shards of more than one part."
  {:arglists '([part-deletes-entry])}
  [[_ part-shards]]
  (some? (next part-shards)))

(defn- merged-deletes
  "Returns the deletes of a transaction (see `add-delete`), merged from
  `part-deletes`, a map from each type to the shards of its deleted resources
  in each part deleting any (see `add-part-deletes`).

  The deleted resources of a type in a single part, which most types of most
  transactions are, need no merge. The shards of the types deleted in several
  parts are merged in parallel on `pool`, one task per shard index, because
  the parts of a large transaction, like a patient purge, often delete
  thousands of resources of the same type, whose ids would be added to a single
  map one after another otherwise."
  [pool part-deletes]
  (let [several (into {} (filter several-parts?) part-deletes)
        single (update-vals (into {} (remove several-parts?) part-deletes) first)]
    (if (empty? several)
      single
      (let [merged-shards (f/fold pool 1 (partial merge-shard several) into
                                  (range impl/num-delete-shards))]
        (into single (map (juxt identity (partial type-shards merged-shards)))
              (keys several))))))

(defn- verify-tx-cmds*
  "Verifies `tx-cmds` in parallel on the `:pool` of `context`, because each
  command depends only on the database before the transaction and not on the
  other commands.

  Returns the verification result with the merged `:deletes` of all parts (see
  `merged-deletes`) or the anomaly of the first failing command."
  [{:keys [pool] :as context} t tx-cmds]
  (when-ok [res (fold-tx-cmds pool part-size empty-res combine-res
                              (partial verify context t) tx-cmds)]
    (assoc res :deletes (merged-deletes pool (:part-deletes res)))))

(defn- add-to-last-part
  "Adds `entries` to the last part of `parts`, a vector of vectors of entries,
  or as the only part if `parts` is empty.

  So the entries of a transaction whose commands fill only one part, which most
  do, are stored with a single put."
  [parts entries]
  (cond
    (empty? entries) parts
    (empty? parts) [entries]
    :else (update parts (dec (count parts)) into entries)))

(defn- post-process-res
  "Completes the verification result of the transaction with `t` by appending
  its TypeStats and SystemStats index entries to the last part of the
  `:entries` the commands produced.

  Verifying a command only accumulates the increments of the type it touches, so
  the entries are built by adding those increments to `stats`, the stats of the
  database before the transaction. That addition yields the stats after the
  transaction as well.

  Returns a map of the `:entries`, the `:stats` after the transaction and the
  `:resource-handle-updates`."
  [stats t {:keys [entries resource-handle-updates] increments :stats}]
  (with-open [_ (prom/timer tx-u/duration-seconds "stats")]
    (let [[stat-entries new-stats] (node-stats/apply-tx stats t increments)]
      {:entries (add-to-last-part entries stat-entries)
       :stats new-stats
       :resource-handle-updates resource-handle-updates})))

(defn- resource-exists? [db type id]
  (when-let [{:keys [op]} (d/resource-handle db type id)]
    (not (identical? :delete op))))

(def ^:private ^:const ^long max-listed-referencing-resources 10)

(defn- format-references [references]
  (str/join ", " (map (fn [[type id]] (format "`%s/%s`" type id)) references)))

(defn- ref-integrity-del-msg
  "Returns the message for the resource with `type` and `id` that should be
  deleted but is still referenced from `references`.

  Lists at most `max-listed-referencing-resources` references, so that the
  message stays small, and adds \"and others\" if there are more."
  [references type id]
  (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from %s%s."
          type id
          (format-references (take max-listed-referencing-resources references))
          (if (< max-listed-referencing-resources (count references)) " and others" "")))

(defn- ref-integrity-del-anom [references type id]
  (ba/conflict (ref-integrity-del-msg references type id)))

(defn- ref-integrity-msg [type id]
  (format "Referential integrity violated. Resource `%s/%s` doesn't exist."
          type id))

(defn- deleted?
  "Returns true if the transaction deletes the resource with `type` and `id`
  according to `deletes` (see `add-delete`).

  A resource already deleted before is in `deletes` only if its delete checks
  references. The transaction doesn't delete it, so that a write referencing it
  fails because it doesn't exist."
  [deletes type id]
  (let [value (some-> (get deletes type) (impl/delete-value id))]
    (or (true? value)
        (and (some? value) (not (rh/deleted? value))))))

(defn- delete-handle
  "Returns the current version of the resource with `type` and `id` from
  `deletes` (see `add-delete`) if its delete checks references or nil
  otherwise."
  [deletes type id]
  (let [value (some-> (get deletes type) (impl/delete-value id))]
    (when-not (true? value)
      value)))

(defn- check-referential-integrity-write
  "Checks the `references` of the resource with `src-type` and `src-id`.

  Returns `existing`, the set of references already found to exist in `db`,
  extended by the ones found here, so that every reference is looked up only
  once per transaction, or the anomaly of the first reference violating the
  referential integrity."
  [db new-resources deletes existing src-type src-id references]
  (reduce
   (fn [existing [type id :as reference]]
     (cond
       (deleted? deletes type id)
       (reduced (ref-integrity-del-anom [[src-type src-id]] type id))

       (or (contains? new-resources reference) (contains? existing reference))
       existing

       (resource-exists? db type id)
       (conj existing reference)

       :else
       (reduced (ba/conflict (ref-integrity-msg type id)))))
   existing
   references))

(defn- local-ref-tuple [rh]
  [(name (:fhir/type rh)) (:id rh)])

(defn- rev-include
  "Returns a reducible collection of the resource handles pointing to
  `resource-handle` by one of the search params of `rev-include-params`,
  skipping the ones in `deletes` (see `add-delete`).

  The reference to `resource-handle` is computed once for all params."
  [db resource-handle rev-include-params deletes]
  (let [reference (rh/tid-id resource-handle)]
    (coll/eduction
     (mapcat #(impl/rev-include db reference % deletes))
     rev-include-params)))

(defn- rev-tuples
  "Returns the local reference tuples of the distinct resources that point to
  `resource-handle` and aren't in `deletes`, at most one more than
  `max-listed-referencing-resources`, which is enough to decide whether the
  message has to state that there are others.

  The resources in `deletes` are skipped before deduplicating, so that
  `distinct` only holds the tuples returned and not every resource pointing to
  `resource-handle` that is deleted along with it."
  [db-before resource-handle rev-include-params deletes]
  (into
   []
   (comp (map local-ref-tuple) (distinct)
         (take (inc max-listed-referencing-resources)))
   (rev-include db-before resource-handle rev-include-params deletes)))

(defn- referential-integrity-delete-anom
  "Returns a conflict anomaly if the resource of `type` with `resource-handle`,
  which is deleted, is still referenced from resources not in `deletes` or nil
  if it isn't."
  [db-before rev-include-params deletes type resource-handle]
  (when-let [references (seq (rev-tuples db-before resource-handle
                                         rev-include-params
                                         deletes))]
    (ref-integrity-del-anom references type (:id resource-handle))))

(defn- rev-include-params-by-type
  "Returns a map from each type of `multiple-deletes`, the types of the
  resources whose deletes check references mapped to whether more than one of
  them is deleted, to the rev-include params of that type, so that they are
  determined only once per transaction and not for each deleted resource.

  The `stats` have to be the ones of `db`."
  [db stats multiple-deletes]
  (into
   {}
   (map (fn [[type multiple-deletes?]]
          [type (impl/rev-include-params db stats type multiple-deletes?)]))
   multiple-deletes))

(defn- delete-rev-include-params
  "Returns the rev-include params of `rev-include-params-by-type` to check the
  references to the resource deleted by `tx-cmd` with.

  Leaves out the `:purged-rev-include-params` of `tx-cmd`, the ones of the
  search params through which the transaction purges every resource
  referencing the resource, as its expansion guarantees. So their index
  entries aren't read once again."
  {:arglists '([rev-include-params-by-type tx-cmd])}
  [rev-include-params-by-type {:keys [type purged-rev-include-params]}]
  (cond->> (rev-include-params-by-type type)
    purged-rev-include-params (into [] (remove purged-rev-include-params))))

(defn- keep-first
  "Returns `existing`, discarding the references found to exist by the commands
  of the part following, because they only save lookups within the part that
  found them. All that matters of the result is whether it's an anomaly."
  [existing _]
  existing)

(def ^:private ^:const ^long ref-check-part-size
  "The number of commands checked in one part of the parallel check of the
  referential integrity.

  Small, because the cost of checking a delete differs a lot between resource
  types, depending on the number of search params pointing to the type and the
  number of resources referencing the deleted one. A part
  usually holds commands of a single type, because the expansion of a patient
  purge orders the commands by type. Small parts spread the commands of an
  expensive type over many workers, so that a few parts don't hold up the
  check while the other workers are idle.

  Many parts are cheap, because combining their results costs nothing (see
  `keep-first`)."
  128)

(defn- referential-integrity-anomaly
  "Checks the referential integrity of `cmds` against `db` with `stats`, using
  the verification result `res` of `cmds`.

  Returns the anomaly of the first command violating it or nil if none does.
  Checks the commands in parallel on `pool`, because each check depends only
  on `db` and `res`.

  The deletes checking references are checked against the current versions in
  the `deletes` of `res`, so that they aren't looked up again. A delete of a
  resource that doesn't exist has none and isn't checked. Neither are the
  references through the `:purged-rev-include-params` of a delete (see
  `delete-rev-include-params`)."
  {:arglists '([db stats pool res cmds])}
  [db stats pool {:keys [new-resources deletes multiple-deletes]} cmds]
  (with-open [_ (prom/timer tx-u/duration-seconds "check-referential-integrity")]
    (let [rev-include-params-by-type (rev-include-params-by-type db stats
                                                                 multiple-deletes)
          result
          (fold-tx-cmds
           pool
           ref-check-part-size
           #{}
           keep-first
           (fn [existing {:keys [op type id refs] :as tx-cmd}]
             (if (#{"delete" "purge"} op)
               (if-let [anom (when-let [resource-handle (delete-handle deletes type id)]
                               (referential-integrity-delete-anom
                                db
                                (delete-rev-include-params
                                 rev-include-params-by-type tx-cmd)
                                deletes type resource-handle))]
                 (reduced anom)
                 existing)
               (if refs
                 (let [existing (check-referential-integrity-write
                                 db new-resources deletes existing type id
                                 refs)]
                   (cond-> existing (ba/anomaly? existing) reduced))
                 existing)))
           cmds)]
      (when (ba/anomaly? result)
        result))))

(defn verify-tx-cmds
  "Verifies terminal `tx-cmds`.

  Returns a map of the `:entries` of the transaction outcome, the `:stats` after
  it and the `:resource-handle-updates` if it is successful or an anomaly if it
  fails. The stats are the ones of `context` with the transaction applied, to be
  taken over by the node after its entries were stored.

  The entries are a vector of parts, one per part of the commands verified in
  parallel, so that the node can store the parts in parallel as well. Entries
  of a transaction are invisible until its success is committed, so that the
  parts don't have to be stored atomically.

  The resource handle updates are tuples of the resource handle of each new
  version and of each purged current version and whether that version is
  purged, to be applied to the resource handle cache of the node, if it has
  one. Purged versions of the history aren't included, because the cache only
  holds current versions. They are in parts like the entries, so that the
  cache can apply them in parallel.

  The commands of a large transaction are verified in parallel on the `:pool`
  of `context`, a ForkJoinPool. A failing transaction still returns the
  anomaly of its first failing command, as if the commands were verified one
  after another.

  A transaction fails if two of its commands act on the same resource, unless
  `:distinct-tx-cmds` of `context` is true, because the caller knows that they
  don't, so that checking them doesn't cost time.

  The `t` is for the new transaction to commit."
  {:arglists '([context t tx-cmds])}
  [{:keys [db-before stats pool distinct-tx-cmds] :as context} t tx-cmds]
  (prom/observe! transaction-sizes (count tx-cmds))
  (ba/try-anomaly
   (or
    (when-not distinct-tx-cmds
      (duplicate-commands-anom tx-cmds))
    (when-ok [res (verify-tx-cmds* (assoc-label-types context tx-cmds) t tx-cmds)]
      (or (referential-integrity-anomaly db-before stats pool res tx-cmds)
          (post-process-res stats t res))))))
