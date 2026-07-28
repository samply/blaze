(ns blaze.page-id-cipher-test
  (:require
   [blaze.async.flow :as flow]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-id-cipher :as page-id-cipher]
   [blaze.page-id-cipher.spec]
   [blaze.scheduler.spec]
   [blaze.scheduler.test-util :as stu]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.google.crypto.tink Aead]
   [java.nio.charset StandardCharsets]
   [java.security GeneralSecurityException]
   [java.util.concurrent Flow$Subscriber Flow$Subscription]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(derive :blaze.db.admin/node :blaze.db/node)

(def config
  {:blaze/page-id-cipher
   {:node (ig/ref :blaze.db.admin/node)
    :scheduler (ig/ref :blaze.test/manual-scheduler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :key-rotation-period (time/seconds 1)}

   :blaze.db.admin/node
   {:tx-log (ig/ref :blaze.db.admin/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.admin/indexer-executor)
    :resource-cache (ig/ref :blaze.db/resource-cache)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :scheduler (ig/ref :blaze/scheduler)
    :poll-timeout (time/millis 10)}

   :blaze.db/resource-cache
   {:resource-store (ig/ref :blaze.db/resource-store)}

   [::tx-log/local :blaze.db.admin/tx-log]
   {:kv-store (ig/ref :blaze.db.admin/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}

   [::kv/mem :blaze.db.admin/transaction-kv-store]
   {:column-families {}}

   [:blaze.db/tx-cache :blaze.db.admin/tx-cache]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)}

   [::node/indexer-executor :blaze.db.node.admin/indexer-executor]
   {}

   [::kv/mem :blaze.db.admin/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   [::node/resource-indexer :blaze.db.node.admin/resource-indexer]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-store (ig/ref :blaze.db/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer.admin/executor)}

   [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.admin/executor]
   {}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}

   ::ts/not-available {}

   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}

   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}

   :blaze.test/manual-scheduler {}

   :blaze.test/fixed-clock {}

   :blaze.test/fixed-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/page-id-cipher nil}
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/page-id-cipher {}}
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "missing scheduler"
    (given-failed-system (update config :blaze/page-id-cipher dissoc :scheduler)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))))

  (testing "missing clock"
    (given-failed-system (update config :blaze/page-id-cipher dissoc :clock)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "missing rng-fn"
    (given-failed-system (update config :blaze/page-id-cipher dissoc :rng-fn)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze/page-id-cipher :node] ::invalid)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config [:blaze/page-id-cipher :scheduler] ::invalid)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze/page-id-cipher :clock] ::invalid)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze/page-id-cipher :rng-fn] ::invalid)
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "success"
    (with-system [{:blaze/keys [page-id-cipher]} config]
      (is (s/valid? :blaze/page-id-cipher page-id-cipher)))))

(defn- rotate-keys!
  "Triggers one key rotation on `manual-scheduler` and returns the state of
  `page-id-cipher` after the rotated key set was published back into it.

  Returns ::timeout if that didn't happen within 10 seconds."
  [{:keys [state]} manual-scheduler]
  ;; the rotated key set is published back into the cipher state asynchronously
  (let [rotated-state-promise (promise)]
    (add-watch state ::rotated
               (fn [_ _ _ new-state] (deliver rotated-state-promise new-state)))
    (stu/tick! manual-scheduler)
    (let [rotated-state (deref rotated-state-promise 10000 ::timeout)]
      (remove-watch state ::rotated)
      rotated-state)))

(deftest key-rotation-test
  (with-system [{:blaze/keys [page-id-cipher]
                 :blaze.test/keys [manual-scheduler]} config]
    (let [state (rotate-keys! page-id-cipher manual-scheduler)]
      (is (not= ::timeout state))

      (given (datafy/datafy (:key-set-handle state))
        count := 2
        [0 :primary] := true
        [0 :status] := :key.status/enabled
        [1 :primary] := false
        [1 :status] := :key.status/enabled))))

(defn- encrypt
  ([cipher plaintext]
   (encrypt cipher plaintext "associated-data"))
  ([^Aead cipher ^String plaintext ^String associated-data]
   (.encrypt cipher (.getBytes plaintext StandardCharsets/UTF_8)
             (.getBytes associated-data StandardCharsets/UTF_8))))

(defn- decrypt
  ([cipher ciphertext]
   (decrypt cipher ciphertext "associated-data"))
  ([^Aead cipher ^bytes ciphertext ^String associated-data]
   (-> (.decrypt cipher ciphertext
                 (.getBytes associated-data StandardCharsets/UTF_8))
       (String. StandardCharsets/UTF_8))))

(deftest encrypt-decrypt-test
  (testing "a value encrypted can be decrypted again"
    (with-system [{:blaze/keys [page-id-cipher]} config]
      (let [ciphertext (encrypt page-id-cipher "plaintext")]
        (is (= "plaintext" (decrypt page-id-cipher ciphertext))))))

  (testing "decryption with different associated data fails"
    (with-system [{:blaze/keys [page-id-cipher]} config]
      (let [ciphertext (encrypt page-id-cipher "plaintext" "associated-data")]
        (is (thrown? GeneralSecurityException
                     (decrypt page-id-cipher ciphertext "other"))))))

  (testing "a value encrypted before a key rotation can still be decrypted after it"
    (with-system [{:blaze/keys [page-id-cipher]
                   :blaze.test/keys [manual-scheduler]} config]
      (let [ciphertext (encrypt page-id-cipher "plaintext")]

        (is (not= ::timeout (rotate-keys! page-id-cipher manual-scheduler)))

        (is (= "plaintext" (decrypt page-id-cipher ciphertext)))))))

(deftest subscriber-error-test
  (testing "an error cancels the subscription"
    (let [cancelled (promise)
          subscription (reify Flow$Subscription
                         (request [_ _])
                         (cancel [_] (deliver cancelled true)))
          subscriber (page-id-cipher/->DocumentReferenceSubscriber
                      nil (atom nil) nil)]
      (flow/on-subscribe! subscriber subscription)

      (.onError ^Flow$Subscriber subscriber (Exception. "msg-160655"))

      (is (true? (deref cancelled 100 ::timeout))))))
