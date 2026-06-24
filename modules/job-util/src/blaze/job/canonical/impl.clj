(ns blaze.job.canonical.impl
  "Up/downgrade transforms behind `blaze.job.canonical/canonicalize`.

  The IG canonicals were migrated from the legacy `samply.github.io` base to the
  current `blaze-server.org` base. A legacy job is *upgraded* — every coded value
  and profile also gains the current canonical, because every element of IG
  version 0.1.0 still exists in the current IG. A current job is *downgraded* —
  only coded values and profiles that existed in IG version 0.1.0 also gain the
  legacy canonical; elements added in later IG versions exist only under the
  current canonical and must not be claimed for 0.1.0.

  `upgrade` and `downgrade` are public so they can be tested directly; production
  code uses `blaze.job.canonical/canonicalize`."
  (:require
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str]))

(def ^:private v0-1-0-profiles
  "StructureDefinition paths the IG defined in version 0.1.0."
  #{"StructureDefinition/Job"
    "StructureDefinition/CompactJob"
    "StructureDefinition/ReIndexJob"
    "StructureDefinition/AsyncInteractionJob"
    "StructureDefinition/AsyncInteractionRequestBundle"
    "StructureDefinition/AsyncInteractionResponseBundle"})

(def ^:private v0-1-0-codes
  "CodeSystem codes the IG defined in version 0.1.0, by CodeSystem path."
  {"CodeSystem/JobType" #{"async-interaction" "async-bulk-data" "compact" "re-index"}
   "CodeSystem/JobStatusReason" #{"orderly-shutdown" "paused" "resumed" "incremented" "started"}
   "CodeSystem/JobCancelledSubStatus" #{"requested" "finished"}
   "CodeSystem/JobOutput" #{"error-category" "error"}
   "CodeSystem/CompactJobParameter" #{"database" "column-family"}
   "CodeSystem/CompactJobOutput" #{"processing-duration"}
   "CodeSystem/ReIndexJobParameter" #{"search-param-url"}
   "CodeSystem/ReIndexJobOutput" #{"total-resources" "resources-processed"
                                   "processing-duration" "next-resource"}
   "CodeSystem/AsyncInteractionJobParameter" #{"bundle" "t"}
   "CodeSystem/AsyncInteractionJobOutput" #{"bundle" "processing-duration"}})

(defn- current-path
  "Returns the canonical path of `url` when it is on the current base, else nil."
  [url]
  (when (and url (str/starts-with? url canonical/base))
    (subs url (inc (count canonical/base)))))

(defn- upgrade-codings [coding]
  (canonical/both-codings coding))

(defn- downgrade-codings [coding]
  (if (contains? (v0-1-0-codes (current-path (-> coding :system :value)))
                 (-> coding :code :value))
    (canonical/both-codings coding)
    [coding]))

(defn- upgrade-profiles [profile]
  (mapv type/canonical (canonical/both-urls (:value profile))))

(defn- downgrade-profiles [profile]
  (if (contains? v0-1-0-profiles (current-path (:value profile)))
    (mapv type/canonical (canonical/both-urls (:value profile)))
    [profile]))

(defn- transform-concept [coding-xf cc]
  (update cc :coding #(into [] (comp (mapcat coding-xf) (distinct)) %)))

(defn- transform [coding-xf profile-xf job]
  (cond-> job
    (-> job :meta :profile seq)
    (update-in [:meta :profile] #(into [] (comp (mapcat profile-xf) (distinct)) %))
    (:code job) (update :code (partial transform-concept coding-xf))
    (:statusReason job) (update :statusReason (partial transform-concept coding-xf))
    (:businessStatus job) (update :businessStatus (partial transform-concept coding-xf))
    (:input job) (update :input (partial mapv #(cond-> % (:type %) (update :type (partial transform-concept coding-xf)))))
    (:output job) (update :output (partial mapv #(cond-> % (:type %) (update :type (partial transform-concept coding-xf)))))))

(defn upgrade
  "Adds the current canonical to every coded value and profile of the legacy job
  `job`, the current canonical first."
  [job]
  (transform upgrade-codings upgrade-profiles job))

(defn downgrade
  "Adds the legacy canonical to every coded value and profile of the current job
  `job` that existed in IG version 0.1.0. Elements added in later versions are
  left with the current canonical only."
  [job]
  (transform downgrade-codings downgrade-profiles job))
