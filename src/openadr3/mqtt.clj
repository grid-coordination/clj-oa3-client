(ns openadr3.mqtt
  "MQTT notification support for OpenADR 3 clients.

  Connects to an MQTT broker and subscribes to VTN notification topics.
  Messages are collected in an atom for later inspection (useful for testing)
  or dispatched to a user-provided callback.

  Usage:
    (require '[openadr3.mqtt :as mqtt])

    ;; Connect and subscribe
    (def conn (mqtt/connect! \"tcp://127.0.0.1:1883\"))
    (mqtt/subscribe! conn [\"programs/+\" \"events/+\"])

    ;; Check received messages
    @(:messages conn)  ;=> [{:topic \"programs/create\" :payload {...}} ...]

    ;; Clean up
    (mqtt/disconnect! conn)"
  (:require [clojurewerkz.machine-head.client :as mh]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [openadr3.entities :as entities])
  (:import [java.nio.charset StandardCharsets]))

(defn- parse-payload
  "Parse MQTT payload bytes as JSON, then coerce if it's a notification.
  Falls back to raw string if not valid JSON."
  [^bytes payload ^String topic]
  (let [s (String. payload StandardCharsets/UTF_8)]
    (try
      (let [parsed (json/read-str s :key-fn keyword)]
        (if (entities/notification? parsed)
          (entities/->notification parsed {:openadr/channel :mqtt
                                           :openadr/topic   topic})
          parsed))
      (catch Exception _
        s))))

(defn connect!
  "Connect to an MQTT broker. Returns a map with :client and :messages atom.

  Options:
    :client-id  — MQTT client ID (default: auto-generated)
    :on-message — optional callback (fn [topic metadata payload-map])
                   called in addition to collecting in :messages"
  ([broker-url]
   (connect! broker-url {}))
  ([broker-url {:keys [client-id on-message]}]
   (let [messages (atom [])
         client   (mh/connect broker-url
                              (cond-> {}
                                client-id (assoc :client-id client-id)))]
     (log/info "MQTT connected" {:broker broker-url :client-id client-id})
     {:client     client
      :broker-url broker-url
      :messages   messages
      :on-message on-message})))

(defn subscribe!
  "Subscribe to one or more MQTT topics. Messages are appended to the
  :messages atom and optionally dispatched to the :on-message callback.

  topics can be a vector of topic strings or a single string."
  [conn topics]
  (let [{:keys [client messages on-message]} conn
        topic-vec  (if (string? topics) [topics] topics)
        topic-map  (zipmap topic-vec (repeat 0))
        handler    (fn [topic _metadata ^bytes payload]
                     (let [parsed  (parse-payload payload topic)
                           msg     {:topic   topic
                                    :payload parsed
                                    :time    (System/currentTimeMillis)}]
                       (log/debug "MQTT message received" {:topic topic})
                       (swap! messages conj msg)
                       (when on-message
                         (on-message topic _metadata parsed))))]
    (mh/subscribe client topic-map handler)
    (log/info "MQTT subscribed" {:topics topic-vec})
    conn))

(defn messages
  "Returns all messages received so far."
  [conn]
  @(:messages conn))

(defn messages-on-topic
  "Returns messages received on a specific topic."
  [conn topic]
  (filterv #(= topic (:topic %)) @(:messages conn)))

(defn clear-messages!
  "Clear collected messages."
  [conn]
  (reset! (:messages conn) [])
  conn)

(defn await-messages
  "Wait until at least n messages have been received, or timeout-ms expires.
  Returns the messages collected. Useful for test assertions."
  ([conn n]
   (await-messages conn n 5000))
  ([conn n timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (let [msgs @(:messages conn)]
         (if (or (>= (count msgs) n)
                 (>= (System/currentTimeMillis) deadline))
           msgs
           (do (Thread/sleep 50)
               (recur))))))))

(defn await-messages-on-topic
  "Wait until at least n messages on a specific topic, or timeout."
  ([conn topic n]
   (await-messages-on-topic conn topic n 5000))
  ([conn topic n timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (let [msgs (messages-on-topic conn topic)]
         (if (or (>= (count msgs) n)
                 (>= (System/currentTimeMillis) deadline))
           msgs
           (do (Thread/sleep 50)
               (recur))))))))

(defn connected?
  "Returns true if the MQTT client is connected."
  [conn]
  (mh/connected? (:client conn)))

(defn disconnect!
  "Disconnect from the MQTT broker and release resources."
  [conn]
  (when (connected? conn)
    (mh/disconnect-and-close (:client conn))
    (log/info "MQTT disconnected" {:broker (:broker-url conn)}))
  conn)
