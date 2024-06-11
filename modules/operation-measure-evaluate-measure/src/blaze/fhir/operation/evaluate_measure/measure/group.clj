(ns blaze.fhir.operation.evaluate-measure.measure.group
  (:require
   [blaze.fhir.operation.evaluate-measure.measure.population :as pop]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as strat]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.luid :as luid]))

(defn- report-stratifier [{:keys [code component]}]
  (cond-> {:fhir/type :fhir.MeasureReport.group/stratifier}
    code
    (assoc :code [code])
    (seq component)
    (assoc :component-codes (:codes (strat/extract-stratifier-components nil component)))))

(defn- initial-group
  ([code]
   (cond-> {:fhir/type :fhir.MeasureReport/group
            :population []}
     code
     (assoc :code code)))
  ([code stratifiers]
   (assoc (initial-group code) :stratifier (mapv report-stratifier stratifiers))))

(defn- initial-state
  ([luid-generator code]
   {:result (initial-group code)
    ::luid/generator luid-generator
    :tx-ops []})
  ([luid-generator code stratifiers]
   {:result (initial-group code stratifiers)
    ::luid/generator luid-generator
    :tx-ops []}))

(defn combine-op-count
  "Returns an combine operator that combines the count results of the
  populations within the group with `code`."
  [luid-generator code]
  (fn
    ([] (initial-state luid-generator code))
    ([state] state)
    ([state [code count]]
     (update-in state [:result :population] conj (pop/population code count)))))

(defn- post-process-strata [strata]
  (mapv strat/stratum-count strata))

(defn- post-process-multi-component-strata [strata component-codes]
  (mapv (partial strat/multi-component-stratum-count component-codes) strata))

(defn- post-process-count-stratifier [{:keys [component-codes] :as stratifier}]
  (if (seq component-codes)
    (-> (update stratifier :stratum post-process-multi-component-strata component-codes)
        (dissoc :component-codes))
    (update stratifier :stratum post-process-strata)))

(defn- post-process-count-stratifiers [stratifiers]
  (mapv post-process-count-stratifier stratifiers))

(defn combine-op-count-stratifier
  "Returns an combine operator that combines the count results of the
  populations within the group with `code` that also has stratifiers."
  [luid-generator code stratifiers]
  (fn
    ([] (initial-state luid-generator code stratifiers))
    ([state]
     (update-in state [:result :stratifier] post-process-count-stratifiers))
    ([state [code {:keys [count strata]}]]
     (update
      state
      :result
      (fn [result]
        (-> (update result :population conj (pop/population code count))
            (update
             :stratifier
             (fn [stratifier]
               (mapv
                (fn [stratifier strata]
                  (update
                   stratifier
                   :stratum
                   #(reduce-kv
                     (fn [stratum value count]
                       (update-in stratum [value code] (fnil + 0) count))
                     %
                     strata)))
                stratifier strata)))))))))

(defn- subject-list-population [code handles list-id]
  (assoc (pop/population code (count handles)) :subjectResults (u/list-reference list-id)))

(defn- combine-handles [{::luid/keys [generator] :keys [result tx-ops]} code handles]
  (let [list-id (luid/head generator)]
    {:result (update result :population conj (subject-list-population code handles list-id))
     ::luid/generator (luid/next generator)
     :tx-ops (into tx-ops (u/population-tx-ops list-id handles))}))

(defn combine-op-subject-list
  "Returns an combine operator that combines the subject-list results of the
  populations within the group with `code`."
  [luid-generator code]
  (fn
    ([] (initial-state luid-generator code))
    ([state] state)
    ([state [code handles]]
     (combine-handles state code handles))))

(defn- append [rf]
  (fn
    ([r] (rf r))
    ([{:keys [result] :as ret} x]
     (update (rf ret x) :result (partial conj result)))))

(defn- post-process-subject-list-stratifier
  [context {:keys [component-codes] :as stratifier}]
  (let [stratum-fn
        (if (seq component-codes)
          (partial strat/multi-component-stratum-subject-list component-codes)
          strat/stratum-subject-list)
        stratifier (dissoc stratifier :component-codes)]
    (transduce
     append
     (fn
       ([ret]
        (update ret :result (partial assoc stratifier :stratum)))
       ([ret [value populations]]
        (stratum-fn ret value populations)))
     (assoc context :result [])
     (:stratum stratifier))))

(defn- post-process-subject-list-stratifier-group [context group]
  (transduce
   append
   (fn
     ([ret]
      (update ret :result (partial assoc group :stratifier)))
     ([ret stratifier]
      (post-process-subject-list-stratifier ret stratifier)))
   (assoc context :result [])
   (:stratifier group)))

(defn combine-op-subject-list-stratifier
  "Returns an combine operator that combines the subject-list results of the
  populations within the group with `code` that also has stratifiers."
  [luid-generator code stratifiers]
  (fn
    ([] (initial-state luid-generator code stratifiers))
    ([{group :result :as state}]
     (post-process-subject-list-stratifier-group state group))
    ([state [code {:keys [handles strata]}]]
     (update-in
      (combine-handles state code handles)
      [:result :stratifier]
      (fn [stratifier]
        (mapv
         (fn [stratifier strata]
           (update
            stratifier
            :stratum
            #(reduce-kv
              (fn [stratum value handles]
                (update-in stratum [value code] (fnil into []) handles))
              %
              strata)))
         stratifier strata))))))
