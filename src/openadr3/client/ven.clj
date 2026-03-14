(ns openadr3.client.ven
  "VEN (Virtual End Node) client component.

  A VEN client represents a single authenticated VEN connection to a VTN.
  It manages VEN registration, notification channels (MQTT/webhook),
  and provides VEN-specific operations like program resolution and
  event polling.

  Usage:
    (require '[openadr3.client.ven :as ven])

    (def v (-> (ven/ven-client {:url \"http://vtn:8080/openadr3/3.1.0\"
                                :token \"ven_token\"})
               component/start))

    ;; Register with the VTN
    (ven/register! v \"my-ven\")

    ;; Add MQTT notifications
    (ven/add-mqtt v \"tcp://broker:1883\")
    (ven/subscribe v :mqtt #(ven/get-mqtt-topics-ven %))

    ;; Add webhook notifications
    (ven/add-webhook v {:port 0 :callback-host \"192.168.1.50\"})

    (component/stop v)"
  (:require [com.stuartsierra.component :as component]
            [openadr3.api :as api]
            [openadr3.channel :as channel]
            [openadr3.client.base :as base]
            [openadr3.entities :as entities]
            [clojure.tools.logging :as log]))

;; ---------------------------------------------------------------------------
;; Component
;; ---------------------------------------------------------------------------

(defrecord VenClient [;; config (provided at construction)
                      url             ; VTN base URL
                      token           ; Bearer token (may be nil if using credentials)
                      client-id       ; OAuth2 client ID (optional)
                      client-secret   ; OAuth2 client secret (optional)
                      spec-version    ; e.g. "3.1.0"
                      ;; runtime (set on start)
                      martian         ; the Martian client instance
                      ;; mutable runtime state
                      state           ; atom — {:ven-id, :ven-name, :channels, :program-cache}
                      ]
  component/Lifecycle

  (start [this]
    (if martian
      (do (log/info "VenClient already started" {:url url})
          this)
      (let [resolved-token (or token
                               (do (log/info "Fetching OAuth2 token" {:client-id client-id})
                                   (base/fetch-token url client-id client-secret)))
            spec-file      (base/spec-path (or spec-version base/default-spec-version))
            m              (api/create-ven-client spec-file resolved-token url)]
        (log/info "VenClient started" {:url url
                                       :spec-version (or spec-version base/default-spec-version)})
        (assoc this :token resolved-token :martian m))))

  (stop [this]
    (when martian
      ;; Auto-stop all channels
      (doseq [[_ ch] (:channels @state)]
        (try
          (channel/channel-stop ch)
          (catch Exception e
            (log/warn "Error stopping channel" {:error (.getMessage e)}))))
      (swap! state assoc :channels {})
      (log/info "VenClient stopped" {:url url}))
    (assoc this :martian nil)))

;; For base/martian accessor compatibility
(defmethod print-method VenClient [v ^java.io.Writer w]
  (.write w (str "#<VenClient " (:url v) ">")))

(defn ven-client
  "Create a VenClient component (not yet started).

  Options:
    :url           — VTN base URL (required)
    :token         — Bearer auth token (provide this OR client-id + client-secret)
    :client-id     — OAuth2 client ID (used with :client-secret)
    :client-secret — OAuth2 client secret (used with :client-id)
    :spec-version  — OpenAPI spec version, default \"3.1.0\"

  Call component/start to connect."
  [{:keys [url token client-id client-secret spec-version]}]
  {:pre [(string? url)
         (or (string? token)
             (and (string? client-id) (string? client-secret)))]}
  (map->VenClient {:url            url
                   :token          token
                   :client-id      client-id
                   :client-secret  client-secret
                   :spec-version   (or spec-version base/default-spec-version)
                   :state          (atom {:channels {} :program-cache {}})}))

;; ---------------------------------------------------------------------------
;; VEN registration
;; ---------------------------------------------------------------------------

(defn ven-id
  "Returns this client's registered VEN ID, or nil if not registered."
  [client]
  (:ven-id @(:state client)))

(defn ven-name
  "Returns this client's registered VEN name, or nil if not registered."
  [client]
  (:ven-name @(:state client)))

(defn- require-ven-id
  "Returns the client's ven-id or throws if not registered."
  [client]
  (or (ven-id client)
      (throw (ex-info "VEN not registered. Call register! first."
                      {}))))

(defn register!
  "Register this VEN with the VTN. Idempotent — finds existing VEN by name
  or creates a new one. Stores the ven-id in the client's state.
  Returns the client for threading."
  [client ven-name]
  (let [m       (base/martian client)
        existing (api/find-ven-by-name m ven-name)
        vid     (if existing
                  (do (log/info "VEN found, reusing registration"
                                {:ven-name ven-name :ven-id (:id existing)})
                      (:id existing))
                  (let [resp (api/create-ven m {:objectType "VEN_VEN_REQUEST"
                                                :venName ven-name})
                        id   (-> resp :body :id)]
                    (when-not id
                      (throw (ex-info "VEN registration failed"
                                      {:ven-name ven-name :status (:status resp)
                                       :body (:body resp)})))
                    (log/info "VEN registered" {:ven-name ven-name :ven-id id})
                    id))]
    (swap! (:state client) assoc :ven-id vid :ven-name ven-name)
    client))

;; ---------------------------------------------------------------------------
;; Channel management
;; ---------------------------------------------------------------------------

(defn add-mqtt
  "Add an MQTT notification channel to this VEN client.
  Creates and starts the channel. Returns the client for threading.

  Options:
    :client-id  — MQTT client ID
    :on-message — callback (fn [topic metadata payload])"
  ([client broker-url]
   (add-mqtt client broker-url {}))
  ([client broker-url opts]
   (let [ch (-> (channel/mqtt-channel broker-url opts)
                channel/channel-start)]
     (swap! (:state client) assoc-in [:channels :mqtt] ch)
     client)))

(defn add-webhook
  "Add a webhook notification channel to this VEN client.
  Creates and starts the channel. Returns the client for threading.

  Options:
    :host          — bind address (default \"0.0.0.0\")
    :port          — bind port, 0 for ephemeral (default 0)
    :path          — callback path (default \"/notifications\")
    :callback-host — hostname in callback URL (default \"127.0.0.1\")
    :bearer-token  — optional auth token
    :on-message    — callback (fn [path payload])"
  ([client]
   (add-webhook client {}))
  ([client opts]
   (let [ch (-> (channel/webhook-channel opts)
                channel/channel-start)]
     (swap! (:state client) assoc-in [:channels :webhook] ch)
     client)))

(defn get-channel
  "Returns the channel of the given type (:mqtt or :webhook), or nil."
  [client channel-type]
  (get-in @(:state client) [:channels channel-type]))

(defn- require-channel
  "Returns the channel or throws."
  [client channel-type]
  (or (get-channel client channel-type)
      (throw (ex-info (str "No " (name channel-type) " channel. Call add-"
                           (name channel-type) " first.")
                      {}))))

(defn subscribe
  "Subscribe to notification topics via a channel.

  For MQTT: topic-fn is called with the client to get a VTN topics
  response, topics are extracted and subscribed on the MQTT channel.

  For webhook: topic-fn is not used (webhook subscriptions are created
  via the VTN API separately). Returns the client for threading."
  [client channel-type topic-fn]
  (let [ch (require-channel client channel-type)]
    (case channel-type
      :mqtt (let [resp   (topic-fn client)
                  topics (base/extract-topics resp)]
              (when topics
                (channel/subscribe-topics ch topics)))
      :webhook nil)
    client))

;; ---------------------------------------------------------------------------
;; VEN-scoped MQTT topic queries (auto-use registered ven-id)
;; ---------------------------------------------------------------------------

(defn get-mqtt-topics-ven
  "Get MQTT topics for this VEN (uses registered ven-id)."
  ([c]    (base/get-mqtt-topics-ven c (require-ven-id c)))
  ([c id] (base/get-mqtt-topics-ven c id)))

(defn get-mqtt-topics-ven-programs
  ([c]    (base/get-mqtt-topics-ven-programs c (require-ven-id c)))
  ([c id] (base/get-mqtt-topics-ven-programs c id)))

(defn get-mqtt-topics-ven-events
  ([c]    (base/get-mqtt-topics-ven-events c (require-ven-id c)))
  ([c id] (base/get-mqtt-topics-ven-events c id)))

(defn get-mqtt-topics-ven-resources
  ([c]    (base/get-mqtt-topics-ven-resources c (require-ven-id c)))
  ([c id] (base/get-mqtt-topics-ven-resources c id)))

;; ---------------------------------------------------------------------------
;; Program resolution with caching
;; ---------------------------------------------------------------------------

(defn resolve-program-id
  "Resolve a program name to its ID, with caching.
  Returns the program ID string or nil if not found."
  [client program-name]
  (let [cache (:program-cache @(:state client))]
    (or (get cache program-name)
        (when-let [prog (api/find-program-by-name (base/martian client) program-name)]
          (let [id (:id prog)]
            (swap! (:state client) assoc-in [:program-cache program-name] id)
            id)))))

;; ---------------------------------------------------------------------------
;; Notifier discovery
;; ---------------------------------------------------------------------------

(defn discover-notifiers
  "Fetch notifier configuration from the VTN.
  Returns the notifiers response body."
  [client]
  (:body (base/get-notifiers client)))

(defn vtn-supports-mqtt?
  "Returns true if the VTN advertises MQTT broker URIs."
  [client]
  (let [notifiers (discover-notifiers client)]
    (boolean (seq (get-in notifiers [:MQTT :URIS])))))

(defn mqtt-broker-urls
  "Discover MQTT broker URLs from the VTN's notifiers endpoint.
  Returns a vector of URI strings, or nil."
  [client]
  (let [notifiers (discover-notifiers client)]
    (when-let [uris (seq (get-in notifiers [:MQTT :URIS]))]
      (vec uris))))

;; ---------------------------------------------------------------------------
;; Event polling
;; ---------------------------------------------------------------------------

(defn poll-events
  "Poll for events relevant to this VEN. Returns coerced event entities.
  Optionally filter by program-id."
  ([client]
   (base/events client))
  ([client {:keys [program-id]}]
   (if program-id
     (let [resp (base/search-events client {:programID program-id})]
       (when (base/success? resp)
         (mapv #(openadr3.entities/->event %) (base/body resp))))
     (base/events client))))
