(ns openadr3.mdns
  "mDNS service discovery for OpenADR 3 VTNs."
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.data :refer [diff]]
            [medley.core :refer [dedupe-by remove-vals map-kv map-vals assoc-some]]
            [mdns.client]
            [util.time.zdt :as zdt]
            [inet.address :as inet]
            [inet.address.six :as inet6]
            [inet.interface :as netface]
            [clojure.tools.logging :as log]))

;; http://billo.systems/inet-address/current/index.html

(defn format-mdns-event
  "Adds [:mdns event-type] timestamp."
  [event-type m]
  (assoc-in m [:mdns event-type] (zdt/now)))

(defn update-item
  "See docstring for `hosts`."
  [items {:keys [name service-type] :as item}]
  (update-in items [service-type name] merge item))

(defn remove-item
  "See docstring for `hosts`."
  [items {:keys [name service-type] :as item}]
  (update-in items [service-type name] dissoc name))

(defmulti process-host-mdns
  (fn [_ event-type _]
    event-type))

(defmethod process-host-mdns :added
  [store event-type {:keys [service-type name] :as parsed-event}]
  (log/info "mDNS, ADDED:" service-type name)
  (let [event (format-mdns-event event-type parsed-event)]
    (swap! store update-item event)))

(defmethod process-host-mdns :resolved
  [store event-type {:keys [service-type name] :as parsed-event}]
  (log/info "mDNS, RESOLVED:" service-type name)
  (let [event (format-mdns-event event-type parsed-event)]
    (swap! store update-item event)))

(defmethod process-host-mdns :removed
  [store event-type {:keys [service-type name] :as parsed-event}]
  (log/info "mDNS, REMOVED:" service-type name)
  (let [event (format-mdns-event event-type parsed-event)]
    (swap! store remove-item event)))

(def hosts
  "Atom to store discovered and resolved hosts.
  {service-type {host1 {record1}
                 host2 {record2}
                 host3 {record3}}}"
  (atom (hash-map)))

(def any-ip
  "Bind to all interface addresses."
  (inet/by-address [0 0 0 0]))

(defn discovery
  "Start mDNS discovery, updating the hosts atom with events."
  [instance hosts]
  (mdns.client/add-service-listener instance (partial process-host-mdns hosts)))

(def mdns-instance (mdns.client/create-instance any-ip))
