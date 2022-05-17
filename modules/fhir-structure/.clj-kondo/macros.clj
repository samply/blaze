(ns macros)


(defmacro def-complex-type
  [name [& fields] & {:keys [fhir-type hash-num interned references field-serializers]
                      :or {interned false}}]
  `(defrecord ~name [~@fields]
     blaze.fhir.spec.type.protocols/FhirType
     (~'-type [~'_])
     (~'-interned [~'_] ~interned)
     ~(if references
        `(~'-references [~'_]
           ~references)
        `(~'-references [~'_]))))
