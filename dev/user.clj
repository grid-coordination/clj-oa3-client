(ns user
  (:require [com.stuartsierra.component :as component]
            [com.brunobonacci.mulog :as mu]
            [openadr3.client.base :as base]
            [openadr3.client.ven :as ven]
            [openadr3.client.bl :as bl]
            [openadr3.channel :as ch]
            [openadr3.discovery :as disc]
            [openadr3.entities :as entities]))

(def vtn-url "http://localhost:8080/openadr3/3.1.0")

;; Start mulog console publisher for REPL use.
;; Returns a stop fn — call (stop-logger) to shut it down.
(defonce ^:private logger-stop-fn
  (mu/start-publisher! {:type :console :pretty? true}))

(defn stop-logger
  "Stop the mulog console publisher."
  []
  (when logger-stop-fn (logger-stop-fn)))

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
            {:ven (ven/ven-client {:url url :token "ven_token"})
             :bl  (bl/bl-client   {:url url :token "bl_token"})}))
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
  ;; Raw API (via base)
  ;; -------------------------------------------------------------------------
  (base/get-programs (bl))
  (base/get-events (ven))
  (base/get-vens (bl))

  ;; -------------------------------------------------------------------------
  ;; Coerced entities
  ;; -------------------------------------------------------------------------
  (base/programs (bl))
  (base/events (ven))
  (base/vens (bl))

  ;; Single entity by ID
  (base/program (bl) "some-id")

  ;; Access raw data
  (-> (first (base/programs (bl))) meta :openadr/raw)

  ;; -------------------------------------------------------------------------
  ;; Introspection
  ;; -------------------------------------------------------------------------
  (sort (base/all-routes (ven)))
  (base/client-type (ven))   ;=> :ven
  (base/scopes (ven))
  (base/authorized? (ven) :search-all-events)

  ;; -------------------------------------------------------------------------
  ;; VEN registration
  ;; -------------------------------------------------------------------------
  (ven/register! (ven) "my-ven")
  (ven/ven-id (ven))        ;=> "abc-123"
  (ven/ven-name (ven))      ;=> "my-ven"

  ;; -------------------------------------------------------------------------
  ;; Program resolution (cached)
  ;; -------------------------------------------------------------------------
  (ven/resolve-program-id (ven) "Program1")  ;=> "42" (API call first time)
  (ven/resolve-program-id (ven) "Program1")  ;=> "42" (cache hit)

  ;; -------------------------------------------------------------------------
  ;; Notifier discovery
  ;; -------------------------------------------------------------------------
  (ven/discover-notifiers (ven))
  (ven/vtn-supports-mqtt? (ven))
  (ven/mqtt-broker-urls (ven))

  ;; -------------------------------------------------------------------------
  ;; MQTT via VenClient channel management
  ;; -------------------------------------------------------------------------
  (ven/add-mqtt (ven) "tcp://localhost:1883")
  (ven/subscribe (ven) :mqtt #(ven/get-mqtt-topics-ven %))

  ;; Check messages
  (ch/channel-messages (ven/get-channel (ven) :mqtt))
  (ch/await-channel-messages (ven/get-channel (ven) :mqtt) 1 5000)
  (ch/clear-channel-messages! (ven/get-channel (ven) :mqtt))

  ;; Channels auto-stop on component/stop

  ;; -------------------------------------------------------------------------
  ;; Webhook via VenClient channel management
  ;; -------------------------------------------------------------------------
  (ven/add-webhook (ven) {:port 0 :callback-host "127.0.0.1"})
  (ch/callback-url (ven/get-channel (ven) :webhook))

  ;; -------------------------------------------------------------------------
  ;; Standalone channels (as Components)
  ;; -------------------------------------------------------------------------
  (def mqtt (component/start (ch/mqtt-channel "tcp://localhost:1883")))
  (ch/subscribe-topics mqtt ["programs/+"])
  (ch/channel-messages mqtt)
  (component/stop mqtt)

  ;; -------------------------------------------------------------------------
  ;; Event polling
  ;; -------------------------------------------------------------------------
  (ven/poll-events (ven))
  (ven/poll-events (ven) {:program-id "42"})

  ;; -------------------------------------------------------------------------
  ;; mDNS discovery (standalone)
  ;; -------------------------------------------------------------------------
  (def d (component/start (disc/mdns-discoverer)))
  (disc/discovered-services d)       ;; async — services trickle in
  (disc/discover-vtns d)             ;; sync — blocks for 5s
  (disc/vtn-urls d)
  (component/stop d)

  ;; -------------------------------------------------------------------------
  ;; mDNS discovery wired into VenClient
  ;; -------------------------------------------------------------------------
  (def sys (component/start-system
            {:discovery (disc/mdns-discoverer)
             :ven (component/using
                   (ven/ven-client {:token "ven_token"})  ;; no URL needed!
                   [:discovery])}))
  ;; VenClient resolves URL from mDNS on start
  (:url (:ven sys))
  (component/stop-system sys)

  ;; -------------------------------------------------------------------------
  ;; Single client (without system)
  ;; -------------------------------------------------------------------------
  (def my-ven (component/start
               (ven/ven-client {:url vtn-url :token "ven_token"})))
  (base/programs my-ven)
  (component/stop my-ven))
