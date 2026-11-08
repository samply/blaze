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
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.kv.spec]
   [blaze.db.node.duplicates :as dup]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.permissions :as p]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.util :as u :refer [str]]
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

(defn- detect-duplicate-commands!
  "Throws a conflict anomaly if two of `cmds` act on the same resource."
  [cmds]
  (when-let [{:keys [cmd existing-cmd]} (dup/find-duplicate cmds)]
    (throw-anom (ba/conflict (dup/duplicate-msg existing-cmd cmd)))))

(defmulti verify
  "Verifies `command`. Returns `res` with added index entries and statistics of
  the transaction outcome.

  Throws an anomaly on conflicts."
  {:arglists '([context t res command])}
  (fn [_context _t _res {:keys [op]}] op))

(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))

(defn- id-collision-msg [type id t]
  (format "Resource `%s/%s` already exists in the database with t = %d and can't be created again." type id t))

(defn- check-id-collision! [db type id]
  (when (d/resource-handle db type id)
    (throw-anom (ba/conflict (id-collision-msg type id (d/t db))))))

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

(defn- check-new-version!
  "Throws a conflict anomaly if the permissions of `context` don't allow to
  write one of the AccessControl codes the new version of `tx-cmd` is labelled
  with.

  The `verb` names the command in the message."
  {:arglists '([context verb tx-cmd])}
  [{:keys [permissions]} verb {:keys [type id access-control]}]
  (when-let [code (first (remove (partial p/may-write? permissions) access-control))]
    (throw-anom (ba/conflict (labelled-new-version-msg verb type id code)))))

(defmethod verify "create"
  [{:keys [db-before] :as context} t res {:keys [type id hash refs] :as tx-cmd}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-create")]
    (check-id-collision! db-before type id)
    (check-new-version! context "create" tx-cmd)
    (let [tid (codec/tid type)]
      (-> (add-version res tid id t hash 1 :create refs)
          (update :new-resources conj [type id])
          (update-in [:stats tid] inc-num-changes-and-total)))))

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

(defn- read-only?
  "Returns true if the `resource-handle` of the current version is tagged
  read-only and the permissions of `context` don't allow to write such
  resources."
  {:arglists '([context resource-handle])}
  [{:keys [db-before read-only-matcher permissions]} resource-handle]
  (and (not (p/may-write? permissions "read-only"))
       (d/matches? db-before read-only-matcher resource-handle)))

(defn- internal?
  "Returns true if the `resource-handle` of the current version is labelled
  internal and the permissions of `context` don't allow to write such
  resources."
  {:arglists '([context resource-handle])}
  [{:keys [db-before internal-matcher permissions]} resource-handle]
  (and (not (p/may-write? permissions "internal"))
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
        (throw-anom (precondition-failed-anomaly if-match type id tx-cmd))

        (and (some? old-t) (= "*" if-none-match))
        (throw-anom (precondition-any-failed-anomaly type id))

        (and (some? old-t) (= if-none-match old-t))
        (throw-anom (precondition-version-failed-anomaly type id if-none-match))

        ;; identical update, we will do nothing
        (= old-hash hash)
        res

        (some->> old-resource-handle (read-only? context))
        (throw-anom (labelled-current-version-anom "update" "read-only" type id))

        (some->> old-resource-handle (internal? context))
        (throw-anom (labelled-current-version-anom "update" "internal" type id))

        :else
        (do (check-new-version! context "update" tx-cmd)
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
        (throw-anom (precondition-failed-anomaly if-match type id tx-cmd))

        (not= hash old-hash)
        (let [msg (format "Keep failed on `%s/%s`." type id)]
          (log/trace msg)
          (throw-anom (ba/conflict msg :blaze.db/tx-cmd tx-cmd)))

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

(defmethod verify "delete"
  [{:keys [db-before] :as context} t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0} :as old-resource-handle}
          (d/resource-handle db-before type id)
          refs (some->> old-resource-handle (patient-refs db-before type))]
      (cond
        (identical? :delete op) res

        (some->> old-resource-handle (read-only? context))
        (throw-anom (labelled-current-version-anom "delete" "read-only" type id))

        (some->> old-resource-handle (internal? context))
        (throw-anom (labelled-current-version-anom "delete" "internal" type id))

        :else
        (cond->
         (-> (add-version res tid id t hash/deleted-hash (inc num-changes) :delete refs)
             (update :del-resources conj [type id])
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

        (internal? context current-handle)
        (throw-anom (labelled-current-version-anom "delete the history of" "internal" type id))

        (too-many-history-entries? instance-history)
        (throw-anom (too-many-history-entries-anom type id))

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
        (internal? context (d/resource-handle db-before type id))
        (throw-anom (labelled-current-version-anom "delete a history version of" "internal" type id))

        (current-version? db-before type id vid)
        (throw-anom (ba/conflict (current-version-msg type id vid)))

        :else
        (add-purge-entries res (codec/tid type) id t [handle]))
      res)))

;; like delete-history but also purges the current version
(defmethod verify "purge"
  [{:keys [db-before] :as context} t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :purge" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-purge")]
    (let [tid (codec/tid type)
          instance-history (limited-instance-history db-before type id)]
      (cond
        (empty? instance-history) res

        (internal? context (first instance-history))
        (throw-anom (labelled-current-version-anom "purge" "internal" type id))

        (too-many-history-entries? instance-history)
        (throw-anom (too-many-history-entries-anom type id))

        :else
        (cond-> (-> (add-purge-entries res tid id t instance-history)
                    ;; the current version is the only one the resource handle
                    ;; cache can hold
                    (update :resource-handle-updates conj
                            [(first instance-history) true]))
          (not (identical? :delete (:op (first instance-history))))
          (-> (update :del-resources conj [type id])
              (update-in [:stats tid :total] dec-0)))))))

(defn- verify-tx-cmds* [context t tx-cmds]
  (reduce
   (partial verify context t)
   {:entries []
    :resource-handle-updates []
    :new-resources #{}
    :del-resources #{}}
   tx-cmds))

(defn- post-process-res
  "Completes the verification result of the transaction with `t` by appending
  its TypeStats and SystemStats index entries to the `:entries` the commands
  produced.

  Verifying a command only accumulates the increments of the type it touches, so
  the entries are built by adding those increments to `stats`, the stats of the
  database before the transaction. That addition yields the stats after the
  transaction as well.

  Returns a map of the `:entries`, the `:stats` after the transaction and the
  `:resource-handle-updates`."
  [stats t {:keys [entries resource-handle-updates] increments :stats}]
  (with-open [_ (prom/timer tx-u/duration-seconds "stats")]
    (let [[stat-entries new-stats] (node-stats/apply-tx stats t increments)]
      {:entries (into entries stat-entries)
       :stats new-stats
       :resource-handle-updates resource-handle-updates})))

(defn- resource-exists? [db type id]
  (when-let [{:keys [op]} (d/resource-handle db type id)]
    (not (identical? :delete op))))

(defn- ref-integrity-del-msg [src-type src-id type id more?]
  (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from `%s/%s`%s."
          type id src-type src-id (if more? " and others" "")))

(defn- ref-integrity-del-anom [src-type src-id type id more?]
  (ba/conflict (ref-integrity-del-msg src-type src-id type id more?)))

(defn- ref-integrity-msg [type id]
  (format "Referential integrity violated. Resource `%s/%s` doesn't exist."
          type id))

(defn- check-referential-integrity-write!
  "Checks the `references` of the resource with `src-type` and `src-id`.

  Returns `existing`, the set of references already found to exist in `db`,
  extended by the ones found here, so that every reference is looked up only
  once per transaction."
  [db new-resources del-resources existing src-type src-id references]
  (reduce
   (fn [existing [type id :as reference]]
     (cond
       (contains? del-resources reference)
       (throw-anom (ref-integrity-del-anom src-type src-id type id false))

       (or (contains? new-resources reference) (contains? existing reference))
       existing

       (resource-exists? db type id)
       (conj existing reference)

       :else
       (throw-anom (ba/conflict (ref-integrity-msg type id)))))
   existing
   references))

(defn- local-ref-tuple [rh]
  [(name (:fhir/type rh)) (:id rh)])

(defn- rev-include
  "Returns a reducible collection of the resource handles pointing to
  `resource-handle` by one of the search params of `rev-include-params`."
  [db resource-handle rev-include-params]
  (coll/eduction
   (mapcat
    (fn [[source-type code]]
      (d/rev-include db resource-handle source-type code)))
   rev-include-params))

(defn- rev-tuples
  "Returns the local reference tuples of at most two distinct resources that
  point to `resource-handle` and aren't in `del-resources`.

  The resources in `del-resources` are removed before deduplicating, so that
  `distinct` only holds the at most two tuples returned and not every resource
  pointing to `resource-handle` that is deleted along with it."
  [db-before resource-handle rev-include-params del-resources]
  (into
   []
   (comp (map local-ref-tuple) (remove del-resources) (distinct) (take 2))
   (rev-include db-before resource-handle rev-include-params)))

(defn- check-referential-integrity-delete!
  [db-before rev-include-params del-resources type id]
  (when-let [resource-handle (d/resource-handle db-before type id)]
    (let [[[type-ref id-ref] second] (rev-tuples db-before resource-handle
                                                 rev-include-params
                                                 del-resources)]
      (when type-ref
        (throw-anom (ref-integrity-del-anom type-ref id-ref type id second))))))

(defn- checked-delete? [{:keys [op check-refs]}]
  (and check-refs (#{"delete" "purge"} op)))

(defn- rev-include-params-by-type
  "Returns a map from the type of each delete in `cmds` that checks references
  to the rev-include params of that type, so that they are determined only once
  per transaction and not for each deleted resource.

  The `stats` have to be the ones of `db`."
  {:arglists '([db stats cmds])}
  [{{:keys [search-param-registry]} :node} stats cmds]
  (into
   {}
   (comp
    (filter checked-delete?)
    (map :type)
    (distinct)
    (map (juxt identity (partial impl/rev-include-params search-param-registry
                                 stats))))
   cmds))

(defn- check-referential-integrity!
  "Checks the referential integrity of `cmds` against `db` with `stats`."
  [db stats {:keys [new-resources del-resources]} cmds]
  (with-open [_ (prom/timer tx-u/duration-seconds "check-referential-integrity")]
    (let [rev-include-params-by-type (rev-include-params-by-type db stats cmds)]
      (reduce
       (fn [existing {:keys [op type id refs check-refs]}]
         (if (#{"delete" "purge"} op)
           (do (when check-refs
                 (check-referential-integrity-delete!
                  db (rev-include-params-by-type type) del-resources type id))
               existing)
           (if refs
             (check-referential-integrity-write!
              db new-resources del-resources existing type id refs)
             existing)))
       #{}
       cmds))))

(defn verify-tx-cmds
  "Verifies terminal `tx-cmds`.

  Returns a map of the `:entries` of the transaction outcome, the `:stats` after
  it and the `:resource-handle-updates` if it is successful or an anomaly if it
  fails. The stats are the ones of `context` with the transaction applied, to be
  taken over by the node after its entries were stored.

  The resource handle updates are tuples of the resource handle of each new
  version and of each purged current version and whether that version is
  purged, to be applied to the resource handle cache of the node, if it has
  one. Purged versions of the history aren't included, because the cache only
  holds current versions.

  The `t` is for the new transaction to commit."
  {:arglists '([context t tx-cmds])}
  [{:keys [db-before stats] :as context} t tx-cmds]
  (prom/observe! transaction-sizes (count tx-cmds))
  (ba/try-anomaly
   (detect-duplicate-commands! tx-cmds)
   (let [res (verify-tx-cmds* context t tx-cmds)]
     (check-referential-integrity! db-before stats res tx-cmds)
     (post-process-res stats t res))))
