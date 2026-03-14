(ns openadr3.client
  "Component-based OpenADR 3 client — facade namespace.

  Provides constructors and re-exports API functions for convenience.
  For notification channels, use openadr3.channel directly.

  Usage:
    (require '[openadr3.client :as client])

    (def c (-> (client/oa3-client {:type :ven
                                   :url \"http://localhost:8080/openadr3/3.1.0\"
                                   :token \"my-token\"})
               component/start))

    (client/register! c \"my-ven\")
    (client/programs c)
    (component/stop c)"
  (:require [openadr3.client.base :as base]
            [openadr3.client.ven :as ven]
            [openadr3.client.bl :as bl]))

;; ---------------------------------------------------------------------------
;; Constructors
;; ---------------------------------------------------------------------------

(defn oa3-client
  "Create an OpenADR 3 client component (not yet started).

  Dispatches to ven-client or bl-client based on :type.

  Options:
    :type          — :ven or :bl (required)
    :url           — VTN base URL (required)
    :token         — Bearer auth token (provide this OR client-id + client-secret)
    :client-id     — OAuth2 client ID (used with :client-secret)
    :client-secret — OAuth2 client secret (used with :client-id)
    :spec-version  — OpenAPI spec version, default \"3.1.0\"

  Call component/start to connect."
  [{:keys [type] :as opts}]
  {:pre [(#{:ven :bl} type)]}
  (let [client-opts (dissoc opts :type)]
    (case type
      :ven (ven/ven-client client-opts)
      :bl  (bl/bl-client client-opts))))

(def ven-client ven/ven-client)
(def bl-client  bl/bl-client)

;; ---------------------------------------------------------------------------
;; Spec resolution (re-exported from base)
;; ---------------------------------------------------------------------------

(def spec-versions       base/spec-versions)
(def default-spec-version base/default-spec-version)
(def spec-path           base/spec-path)
(def fetch-token         base/fetch-token)

;; ---------------------------------------------------------------------------
;; Martian accessor (re-exported from base)
;; ---------------------------------------------------------------------------

(def martian             base/martian)

;; ---------------------------------------------------------------------------
;; VEN registration (delegated to ven module)
;; ---------------------------------------------------------------------------

(def ven-id              ven/ven-id)
(def ven-name            ven/ven-name)
(def register!           ven/register!)

;; ---------------------------------------------------------------------------
;; Raw API access (re-exported from base)
;; ---------------------------------------------------------------------------

;; Programs
(def get-programs        base/get-programs)
(def get-program-by-id   base/get-program-by-id)
(def search-programs     base/search-programs)
(def create-program      base/create-program)
(def update-program      base/update-program)
(def delete-program      base/delete-program)
(def find-program-by-name base/find-program-by-name)

;; Events
(def get-events          base/get-events)
(def get-event-by-id     base/get-event-by-id)
(def search-events       base/search-events)
(def create-event        base/create-event)
(def update-event        base/update-event)
(def delete-event        base/delete-event)

;; VENs
(def get-vens            base/get-vens)
(def search-vens         base/search-vens)
(def get-ven-by-id       base/get-ven-by-id)
(def create-ven          base/create-ven)
(def update-ven          base/update-ven)
(def delete-ven          base/delete-ven)
(def find-ven-by-name    base/find-ven-by-name)

;; Resources
(def search-ven-resources base/search-ven-resources)
(def get-resource-by-id  base/get-resource-by-id)
(def create-resource     base/create-resource)
(def update-resource     base/update-resource)
(def delete-resource     base/delete-resource)

;; Reports
(def get-reports         base/get-reports)
(def get-report-by-id    base/get-report-by-id)
(def search-reports      base/search-reports)
(def create-report       base/create-report)
(def update-report       base/update-report)
(def delete-report       base/delete-report)

;; Subscriptions
(def get-subscriptions   base/get-subscriptions)
(def get-subscription-by-id base/get-subscription-by-id)
(def search-subscriptions base/search-subscriptions)
(def create-subscription base/create-subscription)
(def update-subscription base/update-subscription)
(def delete-subscription base/delete-subscription)

;; Auth & notifiers
(def get-notifiers       base/get-notifiers)
(def get-auth-server     base/get-auth-server)
(def get-token           base/get-token)

;; MQTT topics (non-VEN-scoped)
(def get-mqtt-topics-programs base/get-mqtt-topics-programs)
(def get-mqtt-topics-program base/get-mqtt-topics-program)
(def get-mqtt-topics-program-events base/get-mqtt-topics-program-events)
(def get-mqtt-topics-events base/get-mqtt-topics-events)
(def get-mqtt-topics-reports base/get-mqtt-topics-reports)
(def get-mqtt-topics-subscriptions base/get-mqtt-topics-subscriptions)
(def get-mqtt-topics-vens base/get-mqtt-topics-vens)
(def get-mqtt-topics-resources base/get-mqtt-topics-resources)

;; MQTT topics (VEN-scoped, with auto-ven-id)
(def get-mqtt-topics-ven ven/get-mqtt-topics-ven)
(def get-mqtt-topics-ven-programs ven/get-mqtt-topics-ven-programs)
(def get-mqtt-topics-ven-events ven/get-mqtt-topics-ven-events)
(def get-mqtt-topics-ven-resources ven/get-mqtt-topics-ven-resources)

;; ---------------------------------------------------------------------------
;; Response helpers (re-exported from base)
;; ---------------------------------------------------------------------------

(def success?            base/success?)
(def body                base/body)

;; ---------------------------------------------------------------------------
;; Coerced entity access (re-exported from base)
;; ---------------------------------------------------------------------------

(def programs            base/programs)
(def program             base/program)
(def events              base/events)
(def event               base/event)
(def vens                base/vens)
(def ven                 base/ven)
(def reports             base/reports)
(def report              base/report)
(def subscriptions       base/subscriptions)
(def subscription        base/subscription)

;; ---------------------------------------------------------------------------
;; Introspection (re-exported from base)
;; ---------------------------------------------------------------------------

(def all-routes          base/all-routes)
(def client-type         base/client-type)
(def scopes              base/scopes)
(def endpoint-scopes     base/endpoint-scopes)
(def authorized?         base/authorized?)
