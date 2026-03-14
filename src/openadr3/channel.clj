(ns openadr3.channel
  "Notification channel protocol and implementations.

  Provides a unified interface for receiving OpenADR 3 notifications
  via MQTT or webhooks. Each channel wraps the underlying transport
  (openadr3.mqtt or openadr3.webhook) behind a common protocol.

  Usage:
    (require '[openadr3.channel :as ch])

    ;; Create and start an MQTT channel
    (def mqtt (-> (ch/mqtt-channel \"tcp://broker:1883\")
                  ch/channel-start))
    (ch/subscribe-topics mqtt [\"programs/+\"])
    (ch/channel-messages mqtt)
    (ch/channel-stop mqtt)

    ;; Create and start a webhook channel
    (def wh (-> (ch/webhook-channel {:port 0 :callback-host \"192.168.1.50\"})
                ch/channel-start))
    (ch/callback-url wh)
    (ch/channel-messages wh)
    (ch/channel-stop wh)"
  (:require [openadr3.mqtt :as mqtt]
            [openadr3.webhook :as webhook]
            [clojure.tools.logging :as log]))

(defprotocol NotificationChannel
  "Protocol for notification channels (MQTT, webhook, etc.)."
  (channel-start [ch] "Start the channel (connect to broker / start server).")
  (channel-stop [ch] "Stop the channel (disconnect / stop server).")
  (subscribe-topics [ch topics] "Subscribe to notification topics.")
  (channel-messages [ch] "Get all collected messages.")
  (await-channel-messages [ch n] [ch n timeout-ms]
    "Wait for at least n messages, with optional timeout in ms (default 5000).")
  (clear-channel-messages! [ch] "Clear all collected messages."))

;; ---------------------------------------------------------------------------
;; MQTT channel
;; ---------------------------------------------------------------------------

(defrecord MqttChannel [broker-url opts conn-atom]
  NotificationChannel

  (channel-start [this]
    (let [conn (mqtt/connect! broker-url opts)]
      (reset! conn-atom conn)
      (log/info "MqttChannel started" {:broker broker-url})
      this))

  (channel-stop [this]
    (when-let [conn @conn-atom]
      (mqtt/disconnect! conn)
      (reset! conn-atom nil)
      (log/info "MqttChannel stopped" {:broker broker-url}))
    this)

  (subscribe-topics [this topics]
    (if-let [conn @conn-atom]
      (do (mqtt/subscribe! conn topics)
          this)
      (throw (ex-info "MqttChannel not started. Call channel-start first."
                      {:broker-url broker-url}))))

  (channel-messages [_]
    (when-let [conn @conn-atom]
      (mqtt/messages conn)))

  (await-channel-messages [_ n]
    (mqtt/await-messages @conn-atom n))

  (await-channel-messages [_ n timeout-ms]
    (mqtt/await-messages @conn-atom n timeout-ms))

  (clear-channel-messages! [this]
    (when-let [conn @conn-atom]
      (mqtt/clear-messages! conn))
    this))

;; ---------------------------------------------------------------------------
;; Webhook channel
;; ---------------------------------------------------------------------------

(defrecord WebhookChannel [opts receiver-atom]
  NotificationChannel

  (channel-start [this]
    (let [recv (webhook/start! opts)]
      (reset! receiver-atom recv)
      (log/info "WebhookChannel started" {:port (:port recv)})
      this))

  (channel-stop [this]
    (when-let [recv @receiver-atom]
      (webhook/stop! recv)
      (reset! receiver-atom nil)
      (log/info "WebhookChannel stopped"))
    this)

  (subscribe-topics [this _topics]
    ;; Webhook subscriptions are created via the VTN API, not the channel.
    this)

  (channel-messages [_]
    (when-let [recv @receiver-atom]
      (webhook/messages recv)))

  (await-channel-messages [_ n]
    (webhook/await-messages @receiver-atom n))

  (await-channel-messages [_ n timeout-ms]
    (webhook/await-messages @receiver-atom n timeout-ms))

  (clear-channel-messages! [this]
    (when-let [recv @receiver-atom]
      (webhook/clear-messages! recv))
    this))

;; ---------------------------------------------------------------------------
;; Constructors (no side effects)
;; ---------------------------------------------------------------------------

(defn mqtt-channel
  "Create an MqttChannel (not yet started).

  Options:
    :client-id  — MQTT client ID (default: auto-generated)
    :on-message — callback (fn [topic metadata payload])"
  ([broker-url] (mqtt-channel broker-url {}))
  ([broker-url opts]
   (->MqttChannel broker-url opts (atom nil))))

(defn webhook-channel
  "Create a WebhookChannel (not yet started).

  Options:
    :host          — bind address (default \"0.0.0.0\")
    :port          — bind port, 0 for OS-assigned ephemeral (default 0)
    :path          — callback path (default \"/notifications\")
    :callback-host — hostname/IP used in callback URL (default \"127.0.0.1\")
    :bearer-token  — optional Bearer token for auth verification
    :on-message    — callback (fn [path payload])"
  ([] (webhook-channel {}))
  ([opts]
   (->WebhookChannel opts (atom nil))))

;; ---------------------------------------------------------------------------
;; Channel-specific accessors
;; ---------------------------------------------------------------------------

(defn callback-url
  "Returns the webhook callback URL (only for WebhookChannel)."
  [ch]
  (when-let [recv @(:receiver-atom ch)]
    (webhook/callback-url recv)))

(defn mqtt-connected?
  "Returns true if the MQTT channel is connected."
  [ch]
  (when-let [conn @(:conn-atom ch)]
    (mqtt/connected? conn)))

(defn mqtt-conn
  "Returns the raw MQTT connection map from an MqttChannel."
  [ch]
  @(:conn-atom ch))

(defn webhook-receiver
  "Returns the raw webhook receiver map from a WebhookChannel."
  [ch]
  @(:receiver-atom ch))
