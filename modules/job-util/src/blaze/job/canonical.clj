(ns blaze.job.canonical
  "Adds the counterpart canonical to every coded value and profile of a job
  Task, so a job submitted with one canonical is stored carrying — and
  conforming to — both the current and the legacy (IG version 0.1.0) profile.

  A legacy job is *upgraded* — every coded value and profile also gains the
  current canonical, because every element of IG version 0.1.0 still exists in
  the current IG. A current job is *downgraded* — only coded values and profiles
  that existed in IG version 0.1.0 also gain the legacy canonical. See
  `blaze.job.canonical.impl` for the up/downgrade transforms."
  (:require
   [blaze.fhir.canonical :as canonical]
   [blaze.job.canonical.impl :as impl]
   [clojure.string :as str]))

(defn- on-base? [base value]
  (str/starts-with? value base))

(defn canonicalize
  "Normalizes a submitted job `job` so it carries both the current and legacy
  canonical wherever IG version 0.1.0 supports it. Decided by scanning all
  `meta.profile` canonicals: the job is downgraded when one is on the current
  base, upgraded when one is on the legacy base, and left unchanged when neither
  is found."
  [job]
  (let [profiles (keep :value (-> job :meta :profile))]
    (cond
      (some (partial on-base? canonical/base) profiles) (impl/downgrade job)
      (some (partial on-base? canonical/old-base) profiles) (impl/upgrade job)
      :else job)))
