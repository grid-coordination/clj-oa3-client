(ns user
  (:require [com.stuartsierra.component :as component]
            [openadr3.client :as client]
            [openadr3.entities :as entities]))

(def vtn-url "http://localhost:8080/openadr3/3.1.0")

;; ---------------------------------------------------------------------------
;; Client lifecycle helpers
;; ---------------------------------------------------------------------------

(defonce system (atom nil))

(defn start!
  "Start a system with VEN and BL clients."
  ([] (start! {}))
  ([{:keys [url] :or {url vtn-url}}]
   (reset! system
           (component/start-system
            {:ven (client/oa3-client {:type :ven :url url :token "ven_token"})
             :bl  (client/oa3-client {:type :bl  :url url :token "bl_token"})}))
   (println "System started. Clients: :ven, :bl")))

(defn stop! []
  (when @system
    (component/stop-system @system)
    (reset! system nil)
    (println "System stopped.")))

(defn ven [] (:ven @system))
(defn bl [] (:bl @system))

(comment
  ;; -------------------------------------------------------------------------
  ;; Quick start
  ;; -------------------------------------------------------------------------
  (start!)
  (stop!)

  ;; Or with a different VTN
  (start! {:url "https://my-vtn.example.com/openadr3/3.1.0"})

  ;; -------------------------------------------------------------------------
  ;; Raw API
  ;; -------------------------------------------------------------------------
  (client/get-programs (bl))
  (client/get-events (ven))
  (client/get-vens (bl))

  ;; -------------------------------------------------------------------------
  ;; Coerced entities
  ;; -------------------------------------------------------------------------
  (client/programs (bl))
  (client/events (ven))
  (client/vens (bl))

  ;; Single entity by ID
  (client/program (bl) "some-id")

  ;; Access raw data
  (-> (first (client/programs (bl))) meta :openadr/raw)

  ;; -------------------------------------------------------------------------
  ;; Introspection
  ;; -------------------------------------------------------------------------
  (sort (client/all-routes (ven)))
  (client/client-type (ven))   ;=> :ven
  (client/scopes (ven))
  (client/authorized? (ven) :search-all-events)

  ;; -------------------------------------------------------------------------
  ;; Single client (without system)
  ;; -------------------------------------------------------------------------
  (def my-ven (component/start
               (client/oa3-client {:type :ven
                                   :url vtn-url
                                   :token "ven_token"})))
  (client/programs my-ven)
  (component/stop my-ven))
