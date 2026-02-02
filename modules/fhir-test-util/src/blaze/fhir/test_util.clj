(ns blaze.fhir.test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.executors :as ex]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.structure-definition-repo]
   [integrant.core :as ig]
   [java-time.api :as time])
  (:import
   [com.google.crypto.tink Aead DeterministicAead KeysetHandle RegistryConfiguration]
   [com.google.crypto.tink.daead DeterministicAeadConfig PredefinedDeterministicAeadParameters]
   [java.time Clock Instant]
   [java.util Random]
   [java.util.concurrent Executors TimeUnit]))

(set! *warn-on-reflection* true)
(DeterministicAeadConfig/register)

(defmethod ig/init-key :blaze.test/fixed-clock
  [_ _]
  (time/fixed-clock Instant/EPOCH "UTC"))

(defmethod ig/init-key :blaze.test/offset-clock
  [_ {:keys [clock offset-seconds]}]
  (time/offset-clock clock (time/seconds offset-seconds)))

(defmethod ig/init-key :blaze.test/system-clock
  [_ _]
  (Clock/systemUTC))

(defmethod ig/init-key :blaze.test/fixed-rng-fn
  [_ {:keys [n] :or {n 0}}]
  #(proxy [Random] []
     (nextLong [] n)))

(defmethod ig/init-key :blaze.test/incrementing-rng-fn
  [_ _]
  (let [n (atom -1)]
    #(proxy [Random] []
       (nextLong [] (swap! n inc)))))

(defmethod ig/init-key :blaze.test/fixed-rng
  [_ _]
  (let [state (atom 0)]
    (proxy [Random] []
      (nextBytes [byte-array]
        (assert (= 20 (count byte-array)))
        (let [bb (bb/wrap byte-array)]
          (bb/set-position! bb 12)
          (bb/put-long! bb (swap! state inc)))))))

(defmethod ig/init-key :blaze.test/executor
  [_ _]
  (Executors/newFixedThreadPool 4))

(defmethod ig/halt-key! :blaze.test/executor
  [_ executor]
  (ex/shutdown! executor)
  (ex/await-termination executor 10 TimeUnit/SECONDS))

(defonce structure-definition-repo
  (ig/init-key :blaze.fhir/structure-definition-repo {}))

(defn link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :value :relation)) first :url :value))

(defmethod ig/init-key :blaze.test/page-id-cipher
  [_ _]
  (let [^DeterministicAead aead
        (-> (KeysetHandle/generateNew PredefinedDeterministicAeadParameters/AES256_SIV)
            (.getPrimitive (RegistryConfiguration/get) DeterministicAead))]
    ;; this wraps a DeterministicAead into a normal Aead
    ;; should be only done in tests
    (reify Aead
      (encrypt [_ plaintext associatedData]
        (.encryptDeterministically aead plaintext associatedData))
      (decrypt [_ ciphertext associatedData]
        (.decryptDeterministically aead ciphertext associatedData)))))

(defmethod ig/init-key :blaze.test/json-parser
  [_ {:keys [parsing-context]}]
  (fn [source]
    (fhir-spec/parse-json parsing-context source)))

(defmethod ig/init-key :blaze.test/json-writer
  [_ {:keys [writing-context]}]
  (fn [value]
    (fhir-spec/write-json-as-bytes writing-context value)))

(defn parameter [name]
  (fn [{:keys [parameter]}]
    (filterv #(= name (:value (:name %))) parameter)))

(defn parameter-part [name]
  (fn [{:keys [part]}]
    (filterv #(= name (:value (:name %))) part)))
