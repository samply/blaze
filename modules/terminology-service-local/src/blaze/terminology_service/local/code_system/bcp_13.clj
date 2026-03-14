(ns blaze.terminology-service.local.code-system.bcp-13
  "https://www.rfc-editor.org/bcp/bcp13.txt
  https://datatracker.ietf.org/doc/html/rfc2046
  https://www.iana.org/assignments/media-types/media-types.xhtml"
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(def ^:private code-system
  {:fhir/type :fhir/CodeSystem
   :meta
   #fhir/Meta
    {:tag
     [#fhir/Coding
       {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
        :code #fhir/code "read-only"}]}
   :url #fhir/uri-interned "urn:ietf:bcp:13"
   :version #fhir/string "1.0.0"
   :name #fhir/string "BCP-13"
   :title #fhir/string "BCP-13 Multipurpose Internet Mail Extensions (MIME) types"
   :status #fhir/code "active"
   :experimental #fhir/boolean false
   :content #fhir/code "not-present"})

(defmethod c/find :bcp-13
  [& _]
  (ac/completed-future code-system))

(defmethod c/enhance :bcp-13
  [_ code-system]
  code-system)

(defmethod c/expand-complete :bcp-13
  [_ _]
  (ba/conflict "Expanding all BCP-13 concepts is not possible."))

(defn- valid? [code]
  (let [parts (str/split code #"/")]
    (and (= 2 (count parts))
         (#{"application"
            "audio"
            "example"
            "font"
            "haptics"
            "image"
            "message"
            "model"
            "multipart"
            "text"
            "video"}
          (first parts)))))

(defmethod c/expand-concept :bcp-13
  [_ concepts _]
  (into
   []
   (keep
    (fn [{:keys [code]}]
      (when (valid? (:value code))
        {:system #fhir/uri-interned "urn:ietf:bcp:13"
         :code code})))
   concepts))

(defmethod c/find-complete :bcp-13
  [{:keys [url version]} {{:keys [code]} :clause}]
  (when (valid? code)
    {:code (type/code code) :system url :version version}))

(defn- bcp-13-query [db]
  (d/type-query db "CodeSystem" [["url" "urn:ietf:bcp:13"]]))

(defn- create-code-system [{:keys [node] :as context}]
  (log/debug "Create BCP-13 code system...")
  (d/transact node [(cs-u/tx-op code-system (m/luid context))]))

(defn ensure-code-system
  "Ensures that the BCP-13 code system is present in the database node."
  [{:keys [node] :as context}]
  (if-let [_ (coll/first (bcp-13-query (d/db node)))]
    (ac/completed-future nil)
    (create-code-system context)))
