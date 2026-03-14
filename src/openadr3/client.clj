(ns openadr3.client
  "Component-based OpenADR 3 client.

  Wraps the openadr3.api library with lifecycle management via
  Stuart Sierra's Component. Each OA3Client component represents
  a single authenticated connection to a VTN.

  Usage:
    (require '[openadr3.client :as client])

    ;; Create and start a VEN client (with pre-obtained token)
    (def c (-> (client/oa3-client {:type :ven
                                   :url \"http://localhost:8080/openadr3/3.1.0\"
                                   :token \"my-token\"})
               component/start))

    ;; Or use OAuth2 client credentials (token fetched on start)
    (def c (-> (client/oa3-client {:type :ven
                                   :url \"http://localhost:8080/openadr3/3.1.0\"
                                   :client-id \"ven_client\"
                                   :client-secret \"999\"})
               component/start))

    ;; Register the VEN (stores ven-id in the client's state atom)
    (client/register! c \"my-ven\")

    ;; Use it — all openadr3.api functions work via the :martian key
    (client/get-programs c)
    (client/programs c)       ;; coerced entities

    ;; VEN-scoped calls default to own ven-id when registered
    (client/get-mqtt-topics-ven c)  ;; uses own ven-id
    (client/ven-id c)               ;; => \"abc-123\"

    ;; Stop when done
    (component/stop c)"
  (:require [com.stuartsierra.component :as component]
            [openadr3.api :as api]
            [openadr3.entities :as entities]
            [openadr3.mqtt :as mqtt]
            [clojure.tools.logging :as log]
            [hato.client :as hc]))

;; ---------------------------------------------------------------------------
;; Spec file resolution
;; ---------------------------------------------------------------------------

(def spec-versions
  "Map of OA3 spec version string to classpath resource path."
  {"3.0.0" "openadr3-specification/3.0.0/oadr3.0.0.yaml"
   "3.0.1" "openadr3-specification/3.0.1/openadr3.yaml"
   "3.1.0" "openadr3-specification/3.1.0/openadr3.yaml"
   "3.1.1" "openadr3-specification/3.1.1/openadr3.yaml"})

(def default-spec-version "3.1.0")

(defn spec-path
  "Resolve a spec version string to a classpath resource URL string.
  The spec is on the classpath via the clj-oa3 dependency.
  Throws if the version is unknown or the resource is not found."
  [version]
  (let [resource-path (or (get spec-versions version)
                          (throw (ex-info (str "Unknown OpenADR spec version: " version
                                               ". Known versions: " (keys spec-versions))
                                          {:version version
                                           :known (keys spec-versions)})))
        url (.getResource (clojure.lang.RT/baseLoader) resource-path)]
    (when-not url
      (throw (ex-info (str "OpenAPI spec not found on classpath: " resource-path
                           ". Ensure clj-oa3 is a dependency with the spec symlink in resources/.")
                      {:resource-path resource-path :version version})))
    (.getPath url)))

;; ---------------------------------------------------------------------------
;; OAuth2 token fetch (bypasses Martian — used before client creation)
;; ---------------------------------------------------------------------------

(defn fetch-token
  "Fetch an OAuth2 access token using client credentials grant.

  Discovers the token endpoint via GET {base-url}/auth/server, then
  POSTs to the token URL with the client credentials. Returns the
  access_token string.

  This uses hato directly (not Martian) because we need a token before
  we can create the authenticated Martian client."
  [base-url client-id client-secret]
  (let [base     (.replaceAll ^String base-url "/+$" "")
        http     (hc/build-http-client {:redirect-policy :normal})
        auth-resp (hc/get (str base "/auth/server")
                          {:http-client http :as :json})
        token-url (get-in auth-resp [:body :tokenURL])]
    (when-not token-url
      (throw (ex-info "No tokenURL in auth server response"
                      {:status (:status auth-resp)
                       :body   (:body auth-resp)})))
    (let [token-resp (hc/post token-url
                              {:http-client http
                               :form-params {:grant_type    "client_credentials"
                                             :client_id     client-id
                                             :client_secret client-secret}
                               :as :json})]
      (or (get-in token-resp [:body :access_token])
          (throw (ex-info "No access_token in token response"
                          {:status (:status token-resp)
                           :body   (:body token-resp)}))))))

;; ---------------------------------------------------------------------------
;; Component
;; ---------------------------------------------------------------------------

(defrecord OA3Client [;; config (provided at construction)
                      client-type     ; :ven or :bl
                      url             ; VTN base URL
                      token           ; Bearer token (may be nil if using credentials)
                      client-id       ; OAuth2 client ID (optional)
                      client-secret   ; OAuth2 client secret (optional)
                      spec-version    ; e.g. "3.1.0"
                      ;; runtime (set on start)
                      martian         ; the Martian client instance
                      ;; mutable runtime state (atom, created at construction)
                      state           ; atom — populated by register!, etc.
                      ]
  component/Lifecycle

  (start [this]
    (if martian
      (do (log/info "OA3Client already started" {:type client-type :url url})
          this)
      (let [resolved-token (or token
                               (do (log/info "Fetching OAuth2 token" {:client-id client-id})
                                   (fetch-token url client-id client-secret)))
            spec-file      (spec-path (or spec-version default-spec-version))
            create-fn      (case client-type
                             :ven api/create-ven-client
                             :bl  api/create-bl-client)
            m              (create-fn spec-file resolved-token url)]
        (log/info "OA3Client started" {:type client-type :url url
                                       :spec-version (or spec-version default-spec-version)})
        (assoc this :token resolved-token :martian m))))

  (stop [this]
    (if martian
      (do (log/info "OA3Client stopped" {:type client-type :url url})
          (assoc this :martian nil))
      this)))

(defn oa3-client
  "Create an OA3Client component (not yet started).

  Options:
    :type          — :ven or :bl (required)
    :url           — VTN base URL (required)
    :token         — Bearer auth token (provide this OR client-id + client-secret)
    :client-id     — OAuth2 client ID (used with :client-secret)
    :client-secret — OAuth2 client secret (used with :client-id)
    :spec-version  — OpenAPI spec version, default \"3.1.0\"

  Either :token or both :client-id and :client-secret must be provided.
  When using client credentials, the token is fetched during start via
  the VTN's /auth/server endpoint.

  Call component/start to connect."
  [{:keys [type url token client-id client-secret spec-version]}]
  {:pre [(#{:ven :bl} type)
         (string? url)
         (or (string? token)
             (and (string? client-id) (string? client-secret)))]}
  (map->OA3Client {:client-type    type
                   :url            url
                   :token          token
                   :client-id      client-id
                   :client-secret  client-secret
                   :spec-version   (or spec-version default-spec-version)
                   :state          (atom {})}))

;; ---------------------------------------------------------------------------
;; Convenience accessors — delegate to openadr3.api using :martian
;; ---------------------------------------------------------------------------

(defn martian
  "Get the underlying Martian client from a started OA3Client."
  [client]
  (or (:martian client)
      (throw (ex-info "OA3Client not started. Call component/start first."
                      {:client-type (:client-type client)}))))

;; ---------------------------------------------------------------------------
;; VEN registration — mutable state via :state atom
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
                      {:client-type (:client-type client)}))))

(defn register!
  "Register this VEN with the VTN. Idempotent — finds existing VEN by name
  or creates a new one. Stores the ven-id in the client's state.
  Returns the client for threading."
  [client ven-name]
  (let [m       (martian client)
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

;; Raw API access (returns HTTP responses)

(defn get-programs     [c]   (api/get-programs (martian c)))
(defn get-program-by-id [c id] (api/get-program-by-id (martian c) id))
(defn search-programs  [c q] (api/search-programs (martian c) q))
(defn create-program   [c body] (api/create-program (martian c) body))
(defn update-program   [c id body] (api/update-program (martian c) id body))
(defn delete-program   [c id] (api/delete-program (martian c) id))
(defn find-program-by-name [c name] (api/find-program-by-name (martian c) name))

(defn get-events       [c]   (api/get-events (martian c)))
(defn get-event-by-id  [c id] (api/get-event-by-id (martian c) id))
(defn search-events    [c q] (api/search-events (martian c) q))
(defn create-event     [c body] (api/create-event (martian c) body))
(defn update-event     [c id body] (api/update-event (martian c) id body))
(defn delete-event     [c id] (api/delete-event (martian c) id))

(defn get-vens         [c]   (api/get-vens (martian c)))
(defn search-vens      [c q] (api/search-vens (martian c) q))
(defn get-ven-by-id    [c id] (api/get-ven-by-id (martian c) id))
(defn create-ven       [c body] (api/create-ven (martian c) body))
(defn update-ven       [c id body] (api/update-ven (martian c) id body))
(defn delete-ven       [c id] (api/delete-ven (martian c) id))
(defn find-ven-by-name [c name] (api/find-ven-by-name (martian c) name))

(defn search-ven-resources [c q] (api/search-ven-resources (martian c) q))
(defn get-resource-by-id [c id] (api/get-resource-by-id (martian c) id))
(defn create-resource  [c body] (api/create-resource (martian c) body))
(defn update-resource  [c id body] (api/update-resource (martian c) id body))
(defn delete-resource  [c id] (api/delete-resource (martian c) id))

(defn get-reports      [c]   (api/get-reports (martian c)))
(defn get-report-by-id [c id] (api/get-report-by-id (martian c) id))
(defn search-reports   [c q] (api/search-reports (martian c) q))
(defn create-report    [c body] (api/create-report (martian c) body))
(defn update-report    [c id body] (api/update-report (martian c) id body))
(defn delete-report    [c id] (api/delete-report (martian c) id))

(defn get-subscriptions [c] (api/get-subscriptions (martian c)))
(defn get-subscription-by-id [c id] (api/get-subscription-by-id (martian c) id))
(defn search-subscriptions [c q] (api/search-subscriptions (martian c) q))
(defn create-subscription [c body] (api/create-subscription (martian c) body))
(defn update-subscription [c id body] (api/update-subscription (martian c) id body))
(defn delete-subscription [c id] (api/delete-subscription (martian c) id))

(defn get-notifiers    [c]   (api/get-notifiers (martian c)))
(defn get-auth-server  [c]   (api/get-auth-server (martian c)))
(defn get-token        [c client-id secret] (api/get-token (martian c) client-id secret))

;; MQTT topics
(defn get-mqtt-topics-programs [c] (api/get-mqtt-topics-programs (martian c)))
(defn get-mqtt-topics-program [c id] (api/get-mqtt-topics-program (martian c) id))
(defn get-mqtt-topics-program-events [c id] (api/get-mqtt-topics-program-events (martian c) id))
(defn get-mqtt-topics-ven
  ([c]    (api/get-mqtt-topics-ven (martian c) (require-ven-id c)))
  ([c id] (api/get-mqtt-topics-ven (martian c) id)))
(defn get-mqtt-topics-ven-programs
  ([c]    (api/get-mqtt-topics-ven-programs (martian c) (require-ven-id c)))
  ([c id] (api/get-mqtt-topics-ven-programs (martian c) id)))
(defn get-mqtt-topics-ven-events
  ([c]    (api/get-mqtt-topics-ven-events (martian c) (require-ven-id c)))
  ([c id] (api/get-mqtt-topics-ven-events (martian c) id)))
(defn get-mqtt-topics-ven-resources
  ([c]    (api/get-mqtt-topics-ven-resources (martian c) (require-ven-id c)))
  ([c id] (api/get-mqtt-topics-ven-resources (martian c) id)))
(defn get-mqtt-topics-events [c] (api/get-mqtt-topics-events (martian c)))
(defn get-mqtt-topics-reports [c] (api/get-mqtt-topics-reports (martian c)))
(defn get-mqtt-topics-subscriptions [c] (api/get-mqtt-topics-subscriptions (martian c)))
(defn get-mqtt-topics-vens [c] (api/get-mqtt-topics-vens (martian c)))
(defn get-mqtt-topics-resources [c] (api/get-mqtt-topics-resources (martian c)))

;; Response helpers
(defn success?         [resp] (api/success? resp))
(defn body             [resp] (api/body resp))

;; Coerced entity access (returns namespaced entities with :openadr/raw metadata)

(defn programs         [c]   (api/programs (martian c)))
(defn program          [c id] (api/program (martian c) id))
(defn events           [c]   (api/events (martian c)))
(defn event            [c id] (api/event (martian c) id))
(defn vens             [c]   (api/vens (martian c)))
(defn ven              [c id] (api/ven (martian c) id))
(defn reports          [c]   (api/reports (martian c)))
(defn report           [c id] (api/report (martian c) id))
(defn subscriptions    [c]   (api/subscriptions (martian c)))
(defn subscription     [c id] (api/subscription (martian c) id))

;; Introspection
(defn all-routes       [c]   (api/all-routes (martian c)))
(defn client-type      [c]   (api/client-type (martian c)))
(defn scopes           [c]   (api/scopes (martian c)))
(defn endpoint-scopes  [c ep] (api/endpoint-scopes (martian c) ep))
(defn authorized?      [c ep] (api/authorized? (scopes c) (endpoint-scopes c ep)))

;; ---------------------------------------------------------------------------
;; MQTT notifications
;; ---------------------------------------------------------------------------

(defn connect-mqtt!
  "Connect to an MQTT broker. Stores the connection in the client's state.
  Returns the client for threading.

  Options:
    :client-id  — MQTT client ID (default: auto-generated)
    :on-message — optional callback (fn [topic metadata payload])"
  ([client broker-url]
   (connect-mqtt! client broker-url {}))
  ([client broker-url opts]
   (let [conn (mqtt/connect! broker-url opts)]
     (swap! (:state client) assoc :mqtt conn)
     client)))

(defn mqtt-conn
  "Returns the MQTT connection from the client's state, or nil."
  [client]
  (:mqtt @(:state client)))

(defn- require-mqtt
  "Returns the MQTT connection or throws."
  [client]
  (or (mqtt-conn client)
      (throw (ex-info "MQTT not connected. Call connect-mqtt! first."
                      {:client-type (:client-type client)}))))

(defn- extract-topics
  "Extract topic strings from a VTN MQTT topics response.
  Returns a vector of topic strings."
  [resp]
  (when (<= (:status resp) 299)
    (vals (:topics (:body resp)))))

(defn subscribe-mqtt!
  "Subscribe to MQTT topics. Topics can be a vector of topic strings.
  Returns the client for threading."
  [client topics]
  (mqtt/subscribe! (require-mqtt client) topics)
  client)

(defn subscribe-notifications!
  "Query the VTN for MQTT notification topics and subscribe to them.
  topic-fn is a function that takes the client and returns a response,
  e.g. #(get-mqtt-topics-programs %).
  Returns the client for threading."
  [client topic-fn]
  (let [resp   (topic-fn client)
        topics (extract-topics resp)]
    (when topics
      (subscribe-mqtt! client topics))
    client))

(defn mqtt-messages
  "Returns all MQTT messages received by this client."
  [client]
  (mqtt/messages (require-mqtt client)))

(defn mqtt-messages-on-topic
  "Returns MQTT messages received on a specific topic."
  [client topic]
  (mqtt/messages-on-topic (require-mqtt client) topic))

(defn await-mqtt-messages
  "Wait for at least n MQTT messages, or timeout (default 5000ms)."
  ([client n]
   (mqtt/await-messages (require-mqtt client) n))
  ([client n timeout-ms]
   (mqtt/await-messages (require-mqtt client) n timeout-ms)))

(defn await-mqtt-messages-on-topic
  "Wait for at least n messages on a specific topic, or timeout."
  ([client topic n]
   (mqtt/await-messages-on-topic (require-mqtt client) topic n))
  ([client topic n timeout-ms]
   (mqtt/await-messages-on-topic (require-mqtt client) topic n timeout-ms)))

(defn clear-mqtt-messages!
  "Clear collected MQTT messages."
  [client]
  (mqtt/clear-messages! (require-mqtt client))
  client)

(defn disconnect-mqtt!
  "Disconnect from the MQTT broker."
  [client]
  (when-let [conn (mqtt-conn client)]
    (mqtt/disconnect! conn)
    (swap! (:state client) dissoc :mqtt))
  client)
