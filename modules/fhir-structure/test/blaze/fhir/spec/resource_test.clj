(ns blaze.fhir.spec.resource-test
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private json-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- parse-json
  ([source]
   (res/parse-json json-context source))
  ([type source]
   (res/parse-json json-context type source)))

(defmacro given-parse-json
  {:arglists '([data & body] [type data & body])}
  [type-or-data & more]
  (if (string? type-or-data)
    `(given (parse-json ~type-or-data (j/write-value-as-string ~(first more))) ~@(rest more))
    `(given (parse-json (j/write-value-as-string ~type-or-data)) ~@more)))

(def ^:private cbor-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}))

(defn- parse-cbor
  ([type source]
   (res/parse-cbor cbor-context type :complete source)))

(def ^:private cbor-factory
  (-> (CBORFactory/builder)
      (.build)))

(def ^:private cbor-object-mapper
  (j/object-mapper {:factory cbor-factory :encode-key-fn true}))

(defmacro given-parse-cbor
  {:arglists '[type data & body]}
  [type data & more]
  `(given (parse-cbor ~type (j/write-value-as-bytes ~data cbor-object-mapper)) ~@more))

(deftest parse-json-patient-test
  (testing "unknown property"
    (given-parse-json "Patient"
      {:unknown "foo"}
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unknown property `unknown`."))

  (testing "duplicate property"
    (given (parse-json "Patient" "{\"gender\":\"male\",\"gender\":\"female\"}")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Duplicate property `gender`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

  (testing "wrong value type"
    (given-parse-json "Patient"
      {:id 0}
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."))

  (testing "type autodiscovery"
    (given-parse-json
     {:resourceType "Patient"
      :id "id-122232"}
      :fhir/type := :fhir/Patient
      :id := "id-122232"))

  (testing "id"
    (given-parse-json "Patient"
      {:id "id-122232"}
      :id := "id-122232"))

  (testing "active"
    (given-parse-json "Patient"
      {:active true}
      :active := #fhir/boolean true))

  (testing "name"
    (testing "one name without array"
      (given-parse-json "Patient"
        {:name {:family "family-170804"}}
        :name := [#fhir/HumanName{:family #fhir/string"family-170804"}]))

    (testing "one name"
      (given-parse-json "Patient"
        {:name [{:family "family-170804"}]}
        :name := [#fhir/HumanName{:family #fhir/string"family-170804"}]))

    (testing "two names"
      (given-parse-json "Patient"
        {:name [{:family "family-170813"}
                {:family "family-170825"}]}
        :name := [#fhir/HumanName{:family #fhir/string"family-170813"}
                  #fhir/HumanName{:family #fhir/string"family-170825"}]))

    (testing "invalid primitive string"
      (given-parse-json "Patient"
        {:name "foo"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `HumanName`."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.name")

      (testing "in array"
        (given-parse-json "Patient"
          {:name ["foo"]}
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `HumanName`."
          [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0]")

        (testing "as second value"
          (given-parse-json "Patient"
            {:name [{:family "foo"} "bar"]}
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. Error on value `bar`. Expected type is `HumanName`."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[1]")))))

  (testing "gender"
    (doseq [gender ["male" "female" "other" "unknown"]]
      (given-parse-json "Patient"
        {:gender gender}
        :gender := (type/code gender)))

    (testing "extended properties after value"
      (testing "id"
        (given-parse-json "Patient"
          {:gender "female" :_gender {:id "id-124022"}}
          :gender := #fhir/code{:id "id-124022" :value "female"})

        (testing "invalid"
          (given-parse-json "Patient"
            {:gender "female" :_gender {:id 0}}
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender.id")))

      (testing "extension"
        (testing "one extension without array"
          (given-parse-json "Patient"
            {:gender "female"
             :_gender {:extension {:url "url-102118" :valueString "value-102132"}}}
            :gender := #fhir/code
                        {:extension
                         [#fhir/Extension
                           {:url "url-102118"
                            :value #fhir/string"value-102132"}]
                         :value "female"}))

        (testing "one extension"
          (given-parse-json "Patient"
            {:gender "female"
             :_gender {:extension [{:url "url-102118" :valueString "value-102132"}]}}
            :gender := #fhir/code
                        {:extension
                         [#fhir/Extension
                           {:url "url-102118"
                            :value #fhir/string"value-102132"}]
                         :value "female"}))

        (testing "two extensions"
          (given-parse-json "Patient"
            {:gender "female"
             :_gender {:extension
                       [{:url "url-102118" :valueString "value-102132"}
                        {:url "url-110205" :valueString "value-110211"}]}}
            :gender := #fhir/code
                        {:extension
                         [#fhir/Extension
                           {:url "url-102118"
                            :value #fhir/string"value-102132"}
                          #fhir/Extension
                           {:url "url-110205"
                            :value #fhir/string"value-110211"}]
                         :value "female"}))

        (testing "unknown property"
          (given-parse-json "Patient"
            {:gender "female" :_gender {:prop-161857 "foo"}}
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. Unknown property `prop-161857`."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))

        (testing "parsing error"
          (given (parse-json "Patient" "{\"gender\":\"female\",\"_gender\":{0}}")
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. JSON parsing error."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))))

    (testing "extended properties before value"
      (testing "id"
        (given-parse-json "Patient"
          {:_gender {:id "id-124240"} :gender "other"}
          :gender := #fhir/code{:id "id-124240" :value "other"})

        (testing "invalid"
          (given-parse-json "Patient"
            {:_gender {:id 0} :gender "female"}
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender.id")))

      (testing "unknown property"
        (given-parse-json "Patient"
          {:_gender {:prop-161857 "foo"} :gender "female"}
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Unknown property `prop-161857`."
          [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender")))

    (testing "extended properties without value"
      (testing "id"
        (given-parse-json "Patient"
          {:_gender {:id "id-124240"}}
          :gender := #fhir/code{:id "id-124240"})

        (testing "invalid"
          (given-parse-json "Patient"
            {:_gender {:id 0}}
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
            [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender.id")))

      (testing "unknown property"
        (given-parse-json "Patient"
          {:_gender {:prop-161857 "foo"}}
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Unknown property `prop-161857`."
          [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))

      (testing "invalid"
        (given-parse-json "Patient"
          {:_gender 0}
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `primitive extension map`."
          [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))))

  (testing "birthDate"
    (given-parse-json "Patient"
      {:birthDate "2025"}
      :birthDate := #fhir/date"2025"))

  (testing "deceasedBoolean"
    (doseq [value [true false]]
      (given-parse-json "Patient"
        {:deceasedBoolean value}
        :deceased := (type/boolean value))))

  (testing "deceasedDateTime"
    (given-parse-json "Patient"
      {:deceasedDateTime "2025"}
      :deceased := #fhir/dateTime"2025"))

  (testing "multipleBirthBoolean"
    (given-parse-json "Patient"
      {:multipleBirthBoolean false}
      :multipleBirth := false)

    (testing "extended properties after value"
      (given-parse-json "Patient"
        {:multipleBirthBoolean false
         :_multipleBirthBoolean {:id "id-172212"}}
        :multipleBirth := #fhir/boolean{:id "id-172212" :value false})))

  (testing "multipleBirthInteger"
    (given-parse-json "Patient"
      {:multipleBirthInteger 2}
      :multipleBirth := 2))

  (testing "contact"
    (given-parse-json "Patient"
      {:contact
       [{:gender "female"}]}
      [:contact count] := 1
      [:contact 0 :gender] := #fhir/code"female")))

(deftest parse-json-observation-test
  (testing "unknown property"
    (given-parse-json "Observation"
      {:valueUnknown 0}
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unknown property `valueUnknown`."
      [:fhir/issues 0 :fhir.issues/expression] := "Observation")))

(deftest parse-json-bundle-test
  (given-parse-json "Bundle"
    {:type "collection"
     :entry
     [{:resource
       {:resourceType "Patient"
        :gender "female"}}]}
    ::anom/message := nil
    :fhir/type := :fhir/Bundle
    :type := #fhir/code"collection"
    [:entry count] := 1
    [:entry 0 :fhir/type] := :fhir.Bundle/entry
    [:entry 0 :resource :fhir/type] := :fhir/Patient
    [:entry 0 :resource :gender] := #fhir/code"female"))

(deftest parse-json-questionnaire-test
  (given-parse-json "Questionnaire"
    {:item {:linkId "id-130845"}}
    :fhir/type := :fhir/Questionnaire
    [:item count] := 1
    [:item 0 :linkId] := #fhir/string"id-130845")

  (testing "recursive item"
    (given-parse-json "Questionnaire"
      {:item {:item {:linkId "id-130845"}}}
      :fhir/type := :fhir/Questionnaire
      [:item count] := 1
      [:item 0 :item count] := 1
      [:item 0 :item 0 :linkId] := #fhir/string"id-130845")))

(deftest parse-json-molecular-sequence-test
  (testing "multiple decimal values"
    (doseq [[values extended-properties]
            [[1 [{:id "id-140530"}]]
             [1.1M [{:id "id-140530"}]]
             [[1 2] [nil {:id "id-140556"}]]
             [[1 1.1M] [{:id "id-140622"}]]
             [[1.1M 1] [{:id "id-140636"} {:id "id-140636"}]]
             [[1 2 3] [{:id "id-142643"}]]
             [[nil 2 3] [{:id "id-142643"} nil {:id "id-142842"}]]]
            :let [result-values (cond-> values (number? values) vector)
                  result (mapv #(type/decimal (assoc %2 :value %1))
                               result-values
                               (into extended-properties [nil nil]))]]

      (given-parse-json "MolecularSequence"
        {:quality {:roc {:precision values}}}
        :fhir/type := :fhir/MolecularSequence
        [:quality count] := 1
        [:quality 0 :roc :precision] := (mapv #(some-> % type/decimal) result-values))

      (testing "extended properties before value"
        (given-parse-json "MolecularSequence"
          {:quality {:roc {:_precision extended-properties :precision values}}}
          :fhir/type := :fhir/MolecularSequence
          [:quality count] := 1
          [:quality 0 :roc :precision] := result))

      (testing "extended properties after value"
        (given-parse-json "MolecularSequence"
          {:quality {:roc {:precision values :_precision extended-properties}}}
          :fhir/type := :fhir/MolecularSequence
          [:quality count] := 1
          [:quality 0 :roc :precision] := result)))

    (testing "invalid"
      (doseq [value ["a" ["a"]]]
        (given-parse-json "MolecularSequence"
          {:quality {:roc {:precision value}}}
          ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "MolecularSequence.quality[0].roc.precision"))

      (testing "long out of range"
        (doseq [value ["{\"quality\":{\"roc\":{\"precision\":9999999999999999999}}}"
                       "{\"quality\":{\"roc\":{\"precision\":[9999999999999999999]}}}"]]
          (given (parse-json "MolecularSequence" value)
            ::anom/message := "Invalid JSON representation of a resource. Numeric value (9999999999999999999) out of range of long (-9223372036854775808 - 9223372036854775807)"
            [:fhir/issues 0 :fhir.issues/expression] := "MolecularSequence.quality[0].roc.precision")))

      (testing "end of input"
        (doseq [value ["{\"quality\":{\"roc\":{\"precision\":0"
                       "{\"quality\":{\"roc\":{\"precision\":[0"]]
          (given (parse-json "MolecularSequence" value)
            ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
            [:fhir/issues 0 :fhir.issues/expression] := "MolecularSequence.quality[0].roc")))

      (testing "parsing error"
        (given (parse-json "MolecularSequence" "{\"quality\":{\"roc\":{\"precision\":0e]}}}")
          ::anom/message := "Invalid JSON representation of a resource. JSON parsing error."
          [:fhir/issues 0 :fhir.issues/expression] := "MolecularSequence.quality[0].roc")))

    (testing "duplicate property"
      (doseq [json ["{\"quality\":{\"roc\":{\"precision\":1,\"precision\":1}}}"
                    "{\"quality\":{\"roc\":{\"precision\":[1],\"precision\":1}}}"
                    "{\"quality\":{\"roc\":{\"precision\":1,\"precision\":[1]}}}"
                    "{\"quality\":{\"roc\":{\"precision\":[1],\"precision\":[1]}}}"
                    "{\"quality\":{\"roc\":{\"precision\":1.1,\"precision\":1.1}}}"
                    "{\"quality\":{\"roc\":{\"precision\":[1.1],\"precision\":1.1}}}"
                    "{\"quality\":{\"roc\":{\"precision\":1.1,\"precision\":[1.1]}}}"
                    "{\"quality\":{\"roc\":{\"precision\":[1.1],\"precision\":[1.1]}}}"]]
        (given (parse-json "MolecularSequence" json)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Duplicate property `precision`."
          [:fhir/issues 0 :fhir.issues/expression] := "MolecularSequence.quality[0].roc")))))

(deftest parse-json-extension-test
  (testing "base64Binary"
    (doseq [value ["Zm9vCg==" "YmFyCg==" "k/QGbb8eW94=" "FMtW+yB5nPg="]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueBase64Binary value}
        type/type := :fhir/Extension
        :value := (type/base64Binary value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueBase64Binary "a"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `base64Binary, regex ([0-9a-zA-Z+/=]{4})+`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value"))

    (testing "CBOR parsing allows invalid values"
      (given-parse-cbor "Extension"
                        {:url "foo"
                         :valueBase64Binary "a"}
                        type/type := :fhir/Extension
                        :value := (type/base64Binary "a"))))

  (testing "boolean"
    (doseq [value [true false]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueBoolean value}
        type/type := :fhir/Extension
        :value := (type/boolean value))))

  (testing "canonical"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueCanonical value}
        type/type := :fhir/Extension
        :value := (type/canonical value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueCanonical "\n"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `\n`. Expected type is `canonical, regex [\\u0021-\\uFFFF]*`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "code"
    (doseq [value ["foo" "bar" "a b"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueCode value}
        type/type := :fhir/Extension
        :value := (type/code value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueCode "a  b"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `a  b`. Expected type is `code, regex [\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "date"
    (doseq [value ["2025" "2025-03" "2025-03-15"]]
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate value}
        type/type := :fhir/Extension
        :url := "url-204835"
        :value := (type/date value))

      (testing "extended properties before value"
        (given-parse-json "Extension"
          {:url "url-204835"
           :_valueDate {:id "id-162932"}
           :valueDate value}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/date {:id "id-162932" :value (system/parse-date value)})))

      (testing "extended properties after value"
        (given-parse-json "Extension"
          {:url "url-204835"
           :valueDate value
           :_valueDate {:id "id-162932"}}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/date {:id "id-162932" :value (system/parse-date value)}))))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate "foo"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate "abcd"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `abcd`. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate "2025-02-29"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `2025-02-29`. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "dateTime"
    (doseq [value ["2025" "2025-03" "2025-03-15" "2025-03-15T15:22:13"
                   "2025-03-15T15:22:13Z" "2025-03-15T15:22:13+02:00"]]
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime value}
        type/type := :fhir/Extension
        :url := "url-204835"
        :value := (type/dateTime value)))

    (testing "extended properties before value"
      (doseq [date-time ["2025" "2025-03" "2025-03-15" "2025-03-15T15:22:13"
                         "2025-03-15T15:22:13Z" "2025-03-15T15:22:13+02:00"]]
        (given-parse-json "Extension"
          {:url "url-204835"
           :_valueDateTime {:id "id-162932"}
           :valueDateTime date-time}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/dateTime {:id "id-162932" :value (system/parse-date-time date-time)}))))

    (testing "extended properties after value"
      (doseq [value ["2025" "2025-03" "2025-03-15" "2025-03-15T15:22:13"
                     "2025-03-15T15:22:13Z" "2025-03-15T15:22:13+02:00"]]
        (given-parse-json "Extension"
          {:url "url-204835"
           :valueDateTime value
           :_valueDateTime {:id "id-162932"}}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/dateTime {:id "id-162932" :value (system/parse-date-time value)}))))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime "foo"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date-time`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime "abcd"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `abcd`. Expected type is `date-time`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime "2025-02-29"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `2025-02-29`. Expected type is `date-time`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "decimal"
    (doseq [value [-1 0 1 -1.1M 1.1M]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueDecimal value}
        type/type := :fhir/Extension
        :value := (type/decimal value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDecimal "foo"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `decimal`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDecimal true}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on boolean value true. Expected type is `decimal`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "id"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueId value}
        type/type := :fhir/Extension
        :value := (type/id value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueId 0}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `id`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueId ""}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value ``. Expected type is `id, regex [A-Za-z0-9\\-\\.]{1,64}`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "instant"
    (doseq [value ["2015-02-07T13:28:17.239+02:00" "2017-01-01T00:00:00Z"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueInstant value}
        type/type := :fhir/Extension
        :value := (type/instant value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "foo"
         :valueInstant "2015-02-07T13:28:17.239"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `2015-02-07T13:28:17.239`. Expected type is `instant, regex ([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "integer"
    (doseq [value [-1 0 1]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueInteger value}
        type/type := :fhir/Extension
        :value := (type/integer value))))

  (testing "markdown"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueMarkdown value}
        type/type := :fhir/Extension
        :value := (type/markdown value)))

    (testing "invalid control character"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueMarkdown "foo\u001Ebar"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo\u001Ebar`. Expected type is `markdown, regex [\\r\\n\\t\\u0020-\\uFFFF]+`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "oid"
    (doseq [value ["urn:oid:2.16.840.1.113883.3.1937.777.24.2.1791"
                   "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueOid value}
        type/type := :fhir/Extension
        :value := (type/oid value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueOid "foo"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `oid, regex urn:oid:[0-2](\\.(0|[1-9][0-9]*))+`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "positiveInt"
    (doseq [value [1 2]]
      (given-parse-json "Extension"
        {:url "foo"
         :valuePositiveInt value}
        type/type := :fhir/Extension
        :value := (type/positiveInt value))))

  (testing "string"
    (given-parse-json "Extension"
      {:url "url-204835"
       :valueString "value-204935"}
      type/type := :fhir/Extension
      :url := "url-204835"
      :value := #fhir/string"value-204935")

    (testing "invalid control character"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueString "foo\u001Ebar"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo\u001Ebar`. Expected type is `string, regex [\\r\\n\\t\\u0020-\\uFFFF]+`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "time"
    (doseq [value ["15:22:13" "15:22:13.1" "15:22:13.12" "15:22:13.123"]]
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueTime value}
        type/type := :fhir/Extension
        :url := "url-204835"
        :value := (type/time value)))

    (testing "extended properties before value"
      (doseq [value ["15:22:13" "15:22:13.1" "15:22:13.12" "15:22:13.123"]]
        (given-parse-json "Extension"
          {:url "url-204835"
           :_valueTime {:id "id-162932"}
           :valueTime value}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/time {:id "id-162932" :value (system/parse-time value)}))))

    (testing "extended properties after value"
      (doseq [value ["15:22:13" "15:22:13.1" "15:22:13.12" "15:22:13.123"]]
        (given-parse-json "Extension"
          {:url "url-204835"
           :valueTime value
           :_valueTime {:id "id-162932"}}
          type/type := :fhir/Extension
          :url := "url-204835"
          :value := (type/time {:id "id-162932" :value (system/parse-time value)}))))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueTime "15:60:00"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `15:60:00`. Expected type is `time`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "unsignedInt"
    (doseq [value [0 1]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUnsignedInt value}
        type/type := :fhir/Extension
        :value := (type/unsignedInt value))))

  (testing "uri"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUri value}
        type/type := :fhir/Extension
        :value := (type/uri value)))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueUri "\n"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `\n`. Expected type is `uri, regex [\\u0021-\\uFFFF]*`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "url"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUrl value}
        type/type := :fhir/Extension
        :value := (type/url value)))

    (testing "with id"
      (given-parse-json "Extension"
        {:url "foo"
         :valueUrl "url-171021"
         :_valueUrl {:id "id-170249"}}
        type/type := :fhir/Extension
        :value := (type/url {:id "id-170249" :value "url-171021"})))

    (testing "with extension"
      (given-parse-json "Extension"
        {:url "foo"
         :valueUrl "url-171021"
         :_valueUrl {:extension {:url "url-170854"}}}
        type/type := :fhir/Extension
        :value := (type/url {:extension [#fhir/Extension{:url "url-170854"}] :value "url-171021"})))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueUrl "\n"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `\n`. Expected type is `url, regex [\\u0021-\\uFFFF]*`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "uuid"
    (doseq [value ["urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUuid value}
        type/type := :fhir/Extension
        :value := (type/uuid value)))

    (testing "with id"
      (given-parse-json "Extension"
        {:url "foo"
         :valueUuid "urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"
         :_valueUuid {:id "id-170249"}}
        type/type := :fhir/Extension
        :value := (type/uuid {:id "id-170249" :value "urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"})))

    (testing "with extension"
      (given-parse-json "Extension"
        {:url "foo"
         :valueUuid "urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"
         :_valueUuid {:extension {:url "url-170854"}}}
        type/type := :fhir/Extension
        :value := (type/uuid {:extension [#fhir/Extension{:url "url-170854"}] :value "urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"})))

    (testing "invalid"
      (given-parse-json "Extension"
        {:url "url-204835"
         :valueUuid "invalid-170120"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `invalid-170120`. Expected type is `uuid, regex urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`."
        [:fhir/issues 0 :fhir.issues/expression] := "Extension.value")))

  (testing "Address"
    (given-parse-json "Extension"
      {:url "foo"
       :valueAddress {:city "Leipzig"}}
      type/type := :fhir/Extension
      :value := #fhir/Address{:city #fhir/string"Leipzig"}))

  ;; TODO: Age
  ;; TODO: Annotation

  (testing "Attachment"
    (given-parse-json "Extension"
      {:url "foo"
       :valueAttachment {:contentType "text/plain"}}
      type/type := :fhir/Extension
      :value := #fhir/Attachment{:contentType #fhir/code"text/plain"}))

  (testing "CodeableConcept"
    (given-parse-json "Extension"
      {:url "foo"
       :valueCodeableConcept {:text "text-161119"}}
      type/type := :fhir/Extension
      :value := #fhir/CodeableConcept{:text #fhir/string"text-161119"}))

  (testing "Coding"
    (given-parse-json "Extension"
      {:url "foo"
       :valueCoding {:code "code-161220"}}
      type/type := :fhir/Extension
      :value := #fhir/Coding{:code #fhir/code"code-161220"}))

  (testing "Coding"
    (given-parse-json "Extension"
      {:url "foo"
       :valueCoding {:code "code-161220"}}
      type/type := :fhir/Extension
      :value := #fhir/Coding{:code #fhir/code"code-161220"}))

  ;; TODO: ContactPoint
  ;; TODO: Count
  ;; TODO: Distance
  ;; TODO: Duration

  (testing "HumanName"
    (given-parse-json "Extension"
      {:url "foo"
       :valueHumanName {:family "family-161430"}}
      type/type := :fhir/Extension
      :value := #fhir/HumanName{:family #fhir/string"family-161430"}))

  (testing "Identifier"
    (given-parse-json "Extension"
      {:url "foo"
       :valueIdentifier {:value "value-162019"}}
      type/type := :fhir/Extension
      :value := #fhir/Identifier{:value #fhir/string"value-162019"}))

  ;; TODO: Money

  (testing "Period"
    (given-parse-json "Extension"
      {:url "foo"
       :valuePeriod {:start "2025-03-21"}}
      type/type := :fhir/Extension
      :value := #fhir/Period{:start #fhir/dateTime"2025-03-21"}))

  (testing "Quantity"
    (given-parse-json "Extension"
      {:url "foo"
       :valueQuantity {:value 3.141}}
      type/type := :fhir/Extension
      :value := #fhir/Quantity{:value #fhir/decimal 3.141M}))

  (testing "Range"
    (given-parse-json "Extension"
      {:url "foo"
       :valueRange {:low {:value 3.141}}}
      type/type := :fhir/Extension
      [:value :fhir/type] := :fhir/Range
      [:value :low] := #fhir/Quantity{:value 3.141M}))

  (testing "Ratio"
    (given-parse-json "Extension"
      {:url "foo"
       :valueRatio {:numerator {:value 3.141}}}
      type/type := :fhir/Extension
      :value := #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 3.141M}}))

  (testing "Reference"
    (given-parse-json "Extension"
      {:url "foo"
       :valueReference {:reference "reference-165129"}}
      type/type := :fhir/Extension
      :value := #fhir/Reference{:reference #fhir/string"reference-165129"}))

  ;; TODO: SampledData
  ;; TODO: Signature
  ;; TODO: Timing
  ;; TODO: ContactDetail
  ;; TODO: Contributor
  ;; TODO: DataRequirement

  (testing "Expression"
    (given-parse-json "Extension"
      {:url "foo"
       :valueExpression {:name "name-165516"}}
      type/type := :fhir/Extension
      [:value :fhir/type] := :fhir/Expression
      [:value :name] := #fhir/id"name-165516"))

  ;; TODO: ParameterDefinition
  ;; TODO: RelatedArtifact
  ;; TODO: TriggerDefinition
  ;; TODO: UsageContext
  ;; TODO: Dosage

  (testing "Meta"
    (given-parse-json "Extension"
      {:url "foo"
       :valueMeta {:source "uri-171103"}}
      type/type := :fhir/Extension
      :value := #fhir/Meta{:source #fhir/uri"uri-171103"})))

(deftest parse-json-human-name-test
  (testing "duplicate property"
    (doseq [json ["{\"given\":\"foo\",\"given\":\"bar\"}"
                  "{\"given\":\"foo\",\"given\":[\"bar\"]}"
                  "{\"given\":[\"foo\"],\"given\":\"bar\"}"
                  "{\"given\":[\"foo\"],\"given\":[\"bar\"]}"]]
      (given (parse-json "HumanName" json)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Duplicate property `given`."
        [:fhir/issues 0 :fhir.issues/expression] := "HumanName")))

  (testing "invalid value"
    (doseq [value [0 [0]]]
      (given-parse-json "HumanName"
        {:given value}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string[]`."
        [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given"))

    (doseq [value [0.0 [0.0]]]
      (given-parse-json "HumanName"
        {:given value}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on float value 0.0. Expected type is `string[]`."
        [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given")))

  (testing "Extension"
    (given-parse-json "HumanName"
      {:extension {:url "url-102118" :valueString "value-102132"}}
      type/type := :fhir/HumanName
      :extension := [#fhir/Extension
                      {:url "url-102118"
                       :value #fhir/string"value-102132"}]))

  (testing "family"
    (given-parse-json "HumanName"
      {:family "family-173154"}
      type/type := :fhir/HumanName
      :family := #fhir/string"family-173154"))

  (testing "given"
    (testing "invalid extended properties"
      (doseq [base [{} {:given []} {:given ["foo"]}]
              extended-properties [0 [0]]]
        (given-parse-json "HumanName"
          (assoc base :_given extended-properties)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `primitive extension map`."
          [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given"))

      (doseq [json ["{\"_given\":[" "{\"_given\":{"]]
        (given (parse-json "HumanName" json)
          ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
          [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given"))

      (given (parse-json "HumanName" "{\"_given\":\"")
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input while reading a string value."
        [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given"))

    (testing "no given name value"
      (testing "with extended properties"
        (testing "id"
          (doseq [extended-properties [{:id "id-165848"} [{:id "id-165848"}]]]
            (given-parse-json "HumanName"
              {:_given extended-properties}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:id "id-165848"}]))

          (testing "invalid"
            (doseq [extended-properties [{:id 0} [{:id 0}]]]
              (given-parse-json "HumanName"
                {:_given extended-properties}
                ::anom/category := ::anom/incorrect
                ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
                [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given.id"))))

        (testing "extension"
          (doseq [extensions [{:url "url-182516"} [{:url "url-182516"}]]
                  extended-properties [{:extension extensions} [{:extension extensions}]]]
            (given-parse-json "HumanName"
              {:_given extended-properties}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:extension [#fhir/Extension{:url "url-182516"}]}]))

          (testing "invalid"
            (doseq [extended-properties [{:extension 0} [{:extension 0}]]]
              (given-parse-json "HumanName"
                {:_given extended-properties}
                ::anom/category := ::anom/incorrect
                ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `Extension[]`."
                [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given.extension")))))

      (testing "with empty array"
        (testing "extended properties after the empty array"
          (testing "id"
            (doseq [extended-properties [{:id "id-211339"} [{:id "id-211339"}]]]
              (given-parse-json "HumanName"
                {:given []
                 :_given extended-properties}
                type/type := :fhir/HumanName
                :given := [#fhir/string{:id "id-211339"}]))

            (testing "invalid"
              (doseq [extended-properties [{:id 0} [{:id 0}]]]
                (given-parse-json "HumanName"
                  {:given []
                   :_given extended-properties}
                  ::anom/category := ::anom/incorrect
                  ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
                  [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given.id")))))

        (testing "extended properties before the empty array"
          (doseq [extended-properties [{:id "id-151600"} [{:id "id-151600"}]]]
            (given-parse-json "HumanName"
              {:_given extended-properties
               :given []}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:id "id-151600"}])))))

    (testing "one given name"
      (given-parse-json "HumanName"
        {:given ["given-210928"]}
        type/type := :fhir/HumanName
        :given := [#fhir/string"given-210928"])

      (testing "without array"
        (given-parse-json "HumanName"
          {:given "given-205309"}
          type/type := :fhir/HumanName
          :given := [#fhir/string"given-205309"]))

      (testing "extended properties after value"
        (testing "id"
          (doseq [extended-properties [{:id "id-211339"} [{:id "id-211339"}]]]
            (given-parse-json "HumanName"
              {:given ["given-210928"]
               :_given extended-properties}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:id "id-211339" :value "given-210928"}]))

          (testing "invalid"
            (doseq [extended-properties [{:id 0} [{:id 0}]]]
              (given-parse-json "HumanName"
                {:given ["given-210928"]
                 :_given extended-properties}
                ::anom/category := ::anom/incorrect
                ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
                [:fhir/issues 0 :fhir.issues/expression] := "HumanName.given.id")))))

      (testing "extended properties before value"
        (doseq [extended-properties [{:id "id-151600"} [{:id "id-151600"}]]]
          (given-parse-json "HumanName"
            {:_given extended-properties
             :given ["given-210928"]}
            type/type := :fhir/HumanName
            :given := [#fhir/string{:id "id-151600" :value "given-210928"}]))))

    (testing "two given names"
      (given-parse-json "HumanName"
        {:given ["given-210928" "given-211224"]}
        type/type := :fhir/HumanName
        :given := [#fhir/string"given-210928"
                   #fhir/string"given-211224"])

      (testing "extended properties before value"
        (given-parse-json "HumanName"
          {:_given [{:id "id-151310"} {:id "id-151315"}]
           :given ["given-151318" "given-151323"]}
          type/type := :fhir/HumanName
          :given := [#fhir/string{:id "id-151310" :value "given-151318"}
                     #fhir/string{:id "id-151315" :value "given-151323"}])

        (testing "first given without extended properties"
          (given-parse-json "HumanName"
            {:_given [nil {:id "id-151315"}]
             :given ["given-151318" "given-151323"]}
            type/type := :fhir/HumanName
            :given := [#fhir/string"given-151318"
                       #fhir/string{:id "id-151315" :value "given-151323"}]))

        (testing "second given without extended properties"
          (doseq [extended-properties [{:id "id-151315"} [{:id "id-151315"}] [{:id "id-151315"} nil]]]
            (given-parse-json "HumanName"
              {:_given extended-properties
               :given ["given-151318" "given-151323"]}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:id "id-151315" :value "given-151318"}
                         #fhir/string"given-151323"])))

        (testing "both givens without extended properties"
          (doseq [extended-properties [nil [] [nil] [nil nil] [nil nil nil]]]
            (given-parse-json "HumanName"
              {:_given extended-properties
               :given ["given-151318" "given-151323"]}
              type/type := :fhir/HumanName
              :given := [#fhir/string"given-151318"
                         #fhir/string"given-151323"]))))

      (testing "extended properties after value"
        (given-parse-json "HumanName"
          {:given ["given-151318" "given-151323"]
           :_given [{:id "id-151310"} {:id "id-151315"}]}
          type/type := :fhir/HumanName
          :given := [#fhir/string{:id "id-151310" :value "given-151318"}
                     #fhir/string{:id "id-151315" :value "given-151323"}])

        (testing "first given without extended properties"
          (given-parse-json "HumanName"
            {:given ["given-151318" "given-151323"]
             :_given [nil {:id "id-151315"}]}
            type/type := :fhir/HumanName
            :given := [#fhir/string"given-151318"
                       #fhir/string{:id "id-151315" :value "given-151323"}]))

        (testing "second given without extended properties"
          (doseq [extended-properties [{:id "id-151315"} [{:id "id-151315"}] [{:id "id-151315"} nil]]]
            (given-parse-json "HumanName"
              {:given ["given-151318" "given-151323"]
               :_given extended-properties}
              type/type := :fhir/HumanName
              :given := [#fhir/string{:id "id-151315" :value "given-151318"}
                         #fhir/string"given-151323"])))

        (testing "both givens without extended properties"
          (doseq [extended-properties [nil [] [nil] [nil nil] [nil nil nil]]]
            (given-parse-json "HumanName"
              {:given ["given-151318" "given-151323"]
               :_given extended-properties}
              type/type := :fhir/HumanName
              :given := [#fhir/string"given-151318"
                         #fhir/string"given-151323"])))

        (testing "mixed value and extended properties"
          (given-parse-json "HumanName"
            {:given [nil "given-105549"]
             :_given [{:id "id-105603"} nil]}
            ::anom/message := nil
            type/type := :fhir/HumanName
            :given := [#fhir/string{:id "id-105603"}
                       #fhir/string"given-105549"])))))

  (testing "period"
    (given-parse-json "HumanName"
      {:period {:start "2025"}}
      type/type := :fhir/HumanName
      :period := #fhir/Period{:start #fhir/dateTime"2025"})))

(deftest parse-json-reference-test
  (testing "id"
    (given-parse-json "Reference"
      {:id "id-100759"}
      type/type := :fhir/Reference
      :id := "id-100759"))

  (testing "extension"
    (given-parse-json "Reference"
      {:extension {:url "url-100907"}}
      type/type := :fhir/Reference
      :extension := [#fhir/Extension{:url "url-100907"}]))

  (testing "reference"
    (given-parse-json "Reference"
      {:reference "reference-101044"}
      type/type := :fhir/Reference
      :reference := #fhir/string"reference-101044"))

  (testing "type"
    (given-parse-json "Reference"
      {:type "type-101127"}
      type/type := :fhir/Reference
      :type := #fhir/uri"type-101127"))

  (testing "identifier"
    (given-parse-json "Reference"
      {:identifier {:value "value-101215"}}
      type/type := :fhir/Reference
      :identifier := #fhir/Identifier{:value #fhir/string"value-101215"}))

  (testing "display"
    (given-parse-json "Reference"
      {:display "display-101307"}
      type/type := :fhir/Reference
      :display := #fhir/string"display-101307")))

(deftest parse-json-meta-test
  (testing "versionId"
    (given-parse-json "Meta"
      {:versionId "versionId-104855"}
      type/type := :fhir/Meta
      :versionId := #fhir/id"versionId-104855"))

  (testing "lastUpdated"
    (given-parse-json "Meta"
      {:lastUpdated "0001-01-01T00:00:00Z"}
      type/type := :fhir/Meta
      :lastUpdated := #fhir/instant"0001-01-01T00:00:00Z")

    (testing "extended properties after value"
      (given-parse-json "Meta"
        {:lastUpdated "0001-01-01T00:00:00Z"
         :_lastUpdated {:id "id-111214"}}
        type/type := :fhir/Meta
        :lastUpdated := #fhir/instant{:id "id-111214" :value "0001-01-01T00:00:00Z"})))

  (testing "source"
    (given-parse-json "Meta"
      {:source "source-105040"}
      type/type := :fhir/Meta
      :source := #fhir/uri"source-105040")))

(deftest parse-json-quantity-test
  (testing "invalid"
    (given-parse-json "Quantity"
      {:value "a"}
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `decimal`."
      [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value")

    (testing "long out of range"
      (given (parse-json "Quantity" "{\"value\":9999999999999999999}")
        ::anom/message := "Invalid JSON representation of a resource. Numeric value (9999999999999999999) out of range of long (-9223372036854775808 - 9223372036854775807)"
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))

    (testing "end of input"
      (given (parse-json "Quantity" "{\"value\":0")
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity"))

    (testing "parsing error"
      (given (parse-json "Quantity" "{\"value\":0e}")
        ::anom/message := "Invalid JSON representation of a resource. JSON parsing error."
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity"))

    (testing "invalid array"
      (given (parse-json "Quantity" "{\"value\":[")
        ::anom/message := "Invalid JSON representation of a resource. Error on array start. Expected type is `decimal`."
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))

    (testing "invalid object"
      (given (parse-json "Quantity" "{\"value\":{")
        ::anom/message := "Invalid JSON representation of a resource. Error on object start. Expected type is `decimal`."
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))

    (testing "invalid object"
      (given (parse-json "Quantity" "{\"value\":true")
        ::anom/message := "Invalid JSON representation of a resource. Error on boolean value true. Expected type is `decimal`."
        [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))))

(deftest parse-json-element-definition-test
  (given-parse-json "ElementDefinition"
    {:type [{:code "string"}]}
    ::anom/message := nil
    [:type count] := 1
    [:type 0 :code] := #fhir/uri"string"))
