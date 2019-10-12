(ns blaze.elm.compiler.property-test
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.elm.compiler.property
     :refer [attr scope-expr scope-runtime-type-expr]]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest is]]))


(deftest attr-test
  (are [elm kw] (= kw (attr elm))
    {:resultTypeName "{http://hl7.org/fhir}Specimen.Collection"
     :path "collection"
     :scope "S"
     :type "Property"
     :life/scopes #{"S"}
     :life/source-type "{http://hl7.org/fhir}Specimen"}
    :Specimen/collection

    {:path "collected"
     :type "Property"
     :resultTypeSpecifier
     {:type "ChoiceTypeSpecifier"
      :choice [{:name "{http://hl7.org/fhir}dateTime"
                :type "NamedTypeSpecifier"}
               {:name "{http://hl7.org/fhir}Period"
                :type "NamedTypeSpecifier"}]}
     :source
     {:resultTypeName "{http://hl7.org/fhir}Specimen.Collection"
      :path "collection"
      :scope "S"
      :type "Property"
      :life/scopes #{"S"}
      :life/source-type "{http://hl7.org/fhir}Specimen"}}
    :Specimen.collection/collected))


(deftest scope-expr-test
  (is
    (=
      (-eval
        (scope-expr "C" :CodeableConcept/coding)
        nil
        nil
        {"C" {:CodeableConcept/coding "foo"}})
      "foo")))


(defn stub-entity-type [entity type]
  (st/instrument
    [`datomic-util/entity-type]
    {:spec
     {`datomic-util/entity-type
      (s/fspec
        :args (s/cat :resource #{entity})
        :ret #{type})}
     :stub
     #{`datomic-util/entity-type}}))


(deftest scope-runtime-type-expr-test
  (let [entity {:CodeableConcept/coding "foo"}]
    (stub-entity-type entity "CodeableConcept")
    (is
      (=
        (-eval
          (scope-runtime-type-expr "C" "coding")
          nil
          nil
          {"C" entity})
        "foo"))))
