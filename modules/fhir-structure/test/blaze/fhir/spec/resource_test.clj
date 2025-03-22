(ns blaze.fhir.spec.resource-test
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private context
  (:blaze.fhir/parsing-context
   (ig/init
    {:blaze.fhir/parsing-context
     {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
     :blaze.fhir/structure-definition-repo {}})))

(defn- parse-json
  ([source]
   (res/parse-json context source))
  ([type source]
   (res/parse-json context type source)))

(defmacro given-parse-json
  {:arglists '([data & body] [type data & body])}
  [type-or-data & more]
  (if (string? type-or-data)
    `(given (parse-json ~type-or-data (j/write-value-as-string ~(first more))) ~@(rest more))
    `(given (parse-json (j/write-value-as-string ~type-or-data)) ~@more)))

(deftest parse-json-patient-test
  (testing "unknown property"
    (given-parse-json "Patient"
      {:unknown "foo"}
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unknown property `unknown`."))

  (testing "duplicate property"
    (given (parse-json "Patient" "{\"gender\":\"male\",\"gender\":\"female\"}")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. duplicate property `gender`"))

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
                  #fhir/HumanName{:family #fhir/string"family-170825"}])))

  (testing "gender"
    (doseq [gender ["male" "female" "other" "unknown"]]
      (given-parse-json "Patient"
        {:gender gender}
        :gender := (type/code gender)))

    (testing "extended properties after value"
      (testing "id"
        (given-parse-json "Patient"
          {:gender "female" :_gender {:id "id-124022"}}
          :gender := #fhir/code{:id "id-124022" :value "female"}))

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
                         :value "female"}))))

    (testing "extended properties before value"
      (given-parse-json "Patient"
        {:_gender {:id "id-124240"} :gender "other"}
        :gender := #fhir/code{:id "id-124240" :value "other"})))

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
      (given-parse-json "MolecularSequence"
        {:quality {:roc {:precision "a"}}}
        ::anom/message :# "Invalid JSON representation of a resource. Expected token one of START_ARRAY, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT but was token VALUE_STRING.*")

      (given-parse-json "MolecularSequence"
        {:quality {:roc {:precision ["a"]}}}
        ::anom/message :# "Invalid JSON representation of a resource. Expected token one of VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, END_ARRAY, VALUE_NULL but was token VALUE_STRING.*"))))

(deftest parse-json-extension-test
  (testing "base64Binary"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueBase64Binary value}
        type/type := :fhir/Extension
        :value := (type/base64Binary value))))

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
        :value := (type/canonical value))))

  (testing "code"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueCode value}
        type/type := :fhir/Extension
        :value := (type/code value))))

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
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date`.")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate "abcd"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `abcd`. Expected type is `date`.")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDate "2025-02-29"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `2025-02-29`. Expected type is `date`.")))

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
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date-time`.")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime "abcd"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `abcd`. Expected type is `date-time`.")

      (given-parse-json "Extension"
        {:url "url-204835"
         :valueDateTime "2025-02-29"}
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `2025-02-29`. Expected type is `date-time`.")))

  (testing "decimal"
    (doseq [value [-1 0 1 -1.1M 1.1M]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueDecimal value}
        type/type := :fhir/Extension
        :value := (type/decimal value))))

  (testing "id"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueId value}
        type/type := :fhir/Extension
        :value := (type/id value))))

  (testing "instant"
    (doseq [value ["2015-02-07T13:28:17.239+02:00" "2017-01-01T00:00:00Z"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueInstant value}
        type/type := :fhir/Extension
        :value := (type/instant value))))

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
        :value := (type/markdown value))))

  (testing "oid"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueOid value}
        type/type := :fhir/Extension
        :value := (type/oid value))))

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
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo\u001Ebar`. Expected type is `string, regex [\\r\\n\\t\\u0020-\\uFFFF]+`.")))

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
        ::anom/message := "Invalid JSON representation of a resource. Error on value `15:60:00`. Expected type is `time`.")))

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
        :value := (type/uri value))))

  (testing "url"
    (doseq [value ["foo" "bar"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUrl value}
        type/type := :fhir/Extension
        :value := (type/url value))))

  (testing "UUID"
    (doseq [value ["urn:uuid:29745744-6761-44a9-ab95-fea7e45fc903"]]
      (given-parse-json "Extension"
        {:url "foo"
         :valueUuid value}
        type/type := :fhir/Extension
        :value := (type/uuid value))))

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
    (testing "one given name without array"
      (given-parse-json "HumanName"
        {:given "given-205309"}
        type/type := :fhir/HumanName
        :given := [#fhir/string"given-205309"]))

    (testing "one given name"
      (given-parse-json "HumanName"
        {:given ["given-210928"]}
        type/type := :fhir/HumanName
        :given := [#fhir/string"given-210928"])

      (testing "extended properties after value"
        (given-parse-json "HumanName"
          {:given ["given-210928"]
           :_given [{:id "id-211339"}]}
          type/type := :fhir/HumanName
          :given := [#fhir/string{:id "id-211339" :value "given-210928"}]))

      (testing "extended properties before value"
        (given-parse-json "HumanName"
          {:_given [{:id "id-151600"}]
           :given ["given-210928"]}
          type/type := :fhir/HumanName
          :given := [#fhir/string{:id "id-151600" :value "given-210928"}])))

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
          (given-parse-json "HumanName"
            {:_given [{:id "id-151315"} nil]
             :given ["given-151318" "given-151323"]}
            type/type := :fhir/HumanName
            :given := [#fhir/string{:id "id-151315" :value "given-151318"}
                       #fhir/string"given-151323"]))

        (testing "both givens without extended properties"
          (given-parse-json "HumanName"
            {:_given [nil nil]
             :given ["given-151318" "given-151323"]}
            type/type := :fhir/HumanName
            :given := [#fhir/string"given-151318"
                       #fhir/string"given-151323"])))

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
          (given-parse-json "HumanName"
            {:given ["given-151318" "given-151323"]
             :_given [{:id "id-151315"} nil]}
            type/type := :fhir/HumanName
            :given := [#fhir/string{:id "id-151315" :value "given-151318"}
                       #fhir/string"given-151323"]))

        (testing "both givens without extended properties"
          (given-parse-json "HumanName"
            {:given ["given-151318" "given-151323"]
             :_given [nil nil]}
            type/type := :fhir/HumanName
            :given := [#fhir/string"given-151318"
                       #fhir/string"given-151323"]))

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

(deftest parse-json-element-definition-test
  (given-parse-json "ElementDefinition"
    {:type [{:code "string"}]}
    ::anom/message := nil
    [:type count] := 1
    [:type 0 :code] := #fhir/uri"string"))
