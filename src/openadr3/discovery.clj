(ns openadr3.discovery
  "mDNS service discovery for OpenADR 3 VTNs.

  The MdnsDiscoverer is a Component that creates a JmDNS instance on
  start, discovers VTN services via mDNS, and closes on stop.

  Usage:
    (require '[openadr3.discovery :as disc])

    ;; As a standalone component
    (def d (-> (disc/mdns-discoverer) component/start))
    (disc/discover-vtns d)
    ;; => [{:mdns.service/name \"my-vtn\" :mdns.service/urls [...] ...}]
    (component/stop d)

    ;; In a component system with VenClient
    (component/system-map
      :discovery (disc/mdns-discoverer)
      :ven (component/using
             (ven/ven-client {:token \"tok\"})
             {:discovery :discovery}))"
  (:require [com.stuartsierra.component :as component]
            [mdns.core :as mdns]
            [openadr3.net :as net]
            [com.brunobonacci.mulog :as mu])
  (:import [java.net InetAddress]))

(def default-service-type
  "Default mDNS service type for OpenADR 3 VTNs."
  "_openadr3._tcp.local.")

;; ---------------------------------------------------------------------------
;; Component
;; ---------------------------------------------------------------------------

(defrecord MdnsDiscoverer [;; config
                           service-type  ; mDNS service type to discover
                           bind-address  ; InetAddress to bind JmDNS to
                           ;; runtime (set on start)
                           jmdns         ; JmDNS instance
                           ;; mutable state
                           services      ; atom — vec of discovered service maps
                           ]
  component/Lifecycle

  (start [this]
    (if jmdns
      (do (mu/log ::already-started)
          this)
      (let [addr    (or bind-address
                        (InetAddress/getByName (net/detect-lan-ip)))
            instance (mdns/create addr)
            svcs     (or services (atom []))]
        ;; Listen for events and accumulate resolved services
        (mdns/listen instance (or service-type default-service-type)
                     (fn [{:mdns.event/keys [type service]}]
                       (case type
                         :mdns.event/resolved
                         (do (swap! svcs conj service)
                             (mu/log ::service-discovered
                                     :name (:mdns.service/name service)
                                     :urls (:mdns.service/urls service)))
                         :mdns.event/removed
                         (do (swap! svcs (fn [v] (filterv #(not= (:mdns.service/qualified-name %)
                                                                 (:mdns.service/qualified-name service))
                                                          v)))
                             (mu/log ::service-removed
                                     :name (:mdns.service/name service)))
                         nil)))
        (mu/log ::started
                :service-type (or service-type default-service-type)
                :bind-address (str addr))
        (assoc this :jmdns instance :services svcs))))

  (stop [this]
    (when jmdns
      (mdns/close jmdns)
      (mu/log ::stopped))
    (assoc this :jmdns nil)))

(defn mdns-discoverer
  "Create an MdnsDiscoverer component (not yet started).

  Options:
    :service-type  — mDNS service type (default \"_openadr3._tcp.local.\")
    :bind-address  — java.net.InetAddress to bind to (default: auto-detected LAN IP)"
  ([] (mdns-discoverer {}))
  ([{:keys [service-type bind-address]}]
   (map->MdnsDiscoverer {:service-type (or service-type default-service-type)
                         :bind-address bind-address
                         :services     (atom [])})))

;; ---------------------------------------------------------------------------
;; Discovery API
;; ---------------------------------------------------------------------------

(defn discovered-services
  "Returns all mDNS services discovered so far (resolved events)."
  [discoverer]
  @(:services discoverer))

(defn discover-vtns
  "Synchronously query for VTN services. Blocks for timeout-ms (default 5000)
  while collecting responses. Returns a vector of service maps.

  Can be called on a started MdnsDiscoverer, or will create a temporary
  JmDNS instance if called with just a service type."
  ([discoverer]
   (discover-vtns discoverer 5000))
  ([discoverer timeout-ms]
   (if-let [instance (:jmdns discoverer)]
     (mdns/list-services instance
                         (or (:service-type discoverer) default-service-type)
                         timeout-ms)
     (throw (ex-info "MdnsDiscoverer not started. Call component/start first." {})))))

(defn vtn-urls
  "Extract VTN base URLs from discovered services.
  Returns a vector of URL strings."
  [discoverer]
  (->> (discovered-services discoverer)
       (mapcat :mdns.service/urls)
       (vec)))
