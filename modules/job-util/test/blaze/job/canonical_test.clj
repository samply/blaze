(ns blaze.job.canonical-test
  (:require
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [blaze.job.canonical :as jc]
   [blaze.job.canonical-spec]
   [blaze.job.canonical.impl :as impl]
   [blaze.job.test-util :as jtu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- prepend-profile [job url]
  (update-in job [:meta :profile] #(into [(type/canonical url)] %)))

(deftest canonicalize-test
  (testing "a current job is downgraded"
    (is (= (impl/downgrade (jtu/job canonical/base))
           (jc/canonicalize (jtu/job canonical/base)))))

  (testing "a legacy job is upgraded"
    (is (= (impl/upgrade (jtu/job canonical/old-base))
           (jc/canonicalize (jtu/job canonical/old-base)))))

  (testing "a current canonical found beyond the first profile still downgrades"
    ;; the post-0.1.0 job-type makes downgrade differ from upgrade, so this
    ;; discriminates the profile scan from the old first-profile-only logic
    (let [job (prepend-profile (jtu/job canonical/base :job-type "vacuum")
                               "http://hl7.org/fhir/StructureDefinition/Task")]
      (is (= (impl/downgrade job) (jc/canonicalize job)))))

  (testing "a legacy canonical found beyond the first profile still upgrades"
    (let [job (prepend-profile (jtu/job canonical/old-base)
                               "http://hl7.org/fhir/StructureDefinition/Task")]
      (is (= (impl/upgrade job) (jc/canonicalize job)))))

  (testing "a job without any Blaze canonical is left unchanged"
    (let [job (assoc-in (jtu/job canonical/base) [:meta :profile]
                        [(type/canonical "http://hl7.org/fhir/StructureDefinition/Task")])]
      (is (= job (jc/canonicalize job))))))
