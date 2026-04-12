(ns openadr3.webhook
  "Webhook notification receiver for OpenADR 3 clients.

  Spins up a lightweight HTTP server to receive POST callbacks from the VTN.
  Mirrors the MQTT notification interface (messages, await-messages, clear-messages).

  Uses JDK's built-in com.sun.net.httpserver — no extra dependencies required.

  Usage:
    (require '[openadr3.webhook :as webhook])

    ;; Start a receiver (port 0 = OS-assigned ephemeral port)
    (def recv (webhook/start! {:port 0 :callback-host \"192.168.1.50\"}))

    ;; Get the callback URL to register with the VTN
    (webhook/callback-url recv)
    ;; => \"http://192.168.1.50:54321/notifications\"

    ;; Check received messages
    (webhook/messages recv)

    ;; Clean up
    (webhook/stop! recv)"
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as mu]
            [openadr3.entities :as entities])
  (:import [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
           [java.net InetSocketAddress]
           [java.nio.charset StandardCharsets]))

(defn- read-body
  "Read the full request body as a UTF-8 string."
  [^HttpExchange exchange]
  (String. (.readAllBytes (.getRequestBody exchange)) StandardCharsets/UTF_8))

(defn- send-response
  "Send an HTTP response with the given status and body string."
  [^HttpExchange exchange status body]
  (let [bs (.getBytes ^String body StandardCharsets/UTF_8)]
    (.sendResponseHeaders exchange status (alength bs))
    (with-open [os (.getResponseBody exchange)]
      (.write os bs))))

(defn- parse-webhook-payload
  "Parse webhook payload string. Coerces OpenADR notifications via entities.
  Handles double-encoded JSON (VTN-RI sends the notification as a JSON-encoded
  string inside a JSON body)."
  [^String s ^String path]
  (try
    (let [parsed (json/read-str s :key-fn keyword)
          ;; Handle double-encoded JSON: if first parse yields a string,
          ;; parse again to get the actual map.
          parsed (if (string? parsed)
                   (json/read-str parsed :key-fn keyword)
                   parsed)]
      (if (entities/notification? parsed)
        (entities/->notification parsed {:openadr/channel :webhook
                                         :openadr/path    path})
        parsed))
    (catch Exception _
      s)))

(defn- check-bearer-token
  "Verify Bearer token if configured. Returns true if auth passes."
  [^HttpExchange exchange bearer-token]
  (if-not bearer-token
    true
    (let [auth (or (.getFirst (.getRequestHeaders exchange) "Authorization") "")]
      (= auth (str "Bearer " bearer-token)))))

(defn- make-handler
  "Create an HttpHandler that receives POST notifications."
  [messages bearer-token on-message]
  (reify HttpHandler
    (handle [_ exchange]
      (let [method (.getRequestMethod exchange)
            path   (.getPath (.getRequestURI exchange))]
        (cond
          (= method "GET")
          (send-response exchange 200 "ok")

          (not= method "POST")
          (send-response exchange 405 "Method Not Allowed")

          (not (check-bearer-token exchange bearer-token))
          (send-response exchange 403 "Forbidden")

          :else
          (let [body    (read-body exchange)
                payload (parse-webhook-payload body path)
                msg     {:path    path
                         :payload payload
                         :time    (System/currentTimeMillis)
                         :raw     body}]
            (swap! messages conj msg)
            (mu/log ::notification-received :path path)
            (when on-message
              (on-message path payload))
            (send-response exchange 200 "ok")))))))

(defn start!
  "Start a webhook notification receiver.

  Options:
    :host          — bind address (default \"0.0.0.0\")
    :port          — bind port, 0 for OS-assigned ephemeral (default 0)
    :path          — HTTP path for callbacks (default \"/notifications\")
    :callback-host — hostname/IP used in callback URL (default \"127.0.0.1\")
    :bearer-token  — optional Bearer token for auth verification
    :on-message    — optional callback (fn [path payload])

  Returns a receiver map. Use callback-url to get the URL to register
  with the VTN."
  ([]
   (start! {}))
  ([{:keys [host port path callback-host bearer-token on-message]
     :or   {host "0.0.0.0" port 0 path "/notifications"
            callback-host "127.0.0.1"}}]
   (let [messages (atom [])
         handler  (make-handler messages bearer-token on-message)
         server   (HttpServer/create (InetSocketAddress. ^String host ^int port) 0)]
     (.createContext server path handler)
     (.start server)
     (let [actual-port (.getPort (.getAddress server))]
       (mu/log ::started
               :callback-host callback-host :port actual-port :path path)
       {:server        server
        :host          host
        :port          actual-port
        :path          path
        :callback-host callback-host
        :bearer-token  bearer-token
        :messages      messages}))))

(defn callback-url
  "Returns the callback URL to register with the VTN."
  [recv]
  (str "http://" (:callback-host recv) ":" (:port recv) (:path recv)))

(defn messages
  "Returns all messages received so far."
  [recv]
  @(:messages recv))

(defn messages-on-path
  "Returns messages received on a specific path."
  [recv path]
  (filterv #(= path (:path %)) @(:messages recv)))

(defn clear-messages!
  "Clear collected messages."
  [recv]
  (reset! (:messages recv) [])
  recv)

(defn await-messages
  "Wait until at least n messages have been received, or timeout-ms expires.
  Returns the messages collected."
  ([recv n]
   (await-messages recv n 5000))
  ([recv n timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (let [msgs @(:messages recv)]
         (if (or (>= (count msgs) n)
                 (>= (System/currentTimeMillis) deadline))
           msgs
           (do (Thread/sleep 50)
               (recur))))))))

(defn await-messages-on-path
  "Wait until at least n messages on a specific path, or timeout."
  ([recv path n]
   (await-messages-on-path recv path n 5000))
  ([recv path n timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (let [msgs (messages-on-path recv path)]
         (if (or (>= (count msgs) n)
                 (>= (System/currentTimeMillis) deadline))
           msgs
           (do (Thread/sleep 50)
               (recur))))))))

(defn stop!
  "Stop the webhook server."
  [recv]
  (when-let [^HttpServer server (:server recv)]
    (.stop server 1)
    (mu/log ::stopped :port (:port recv)))
  recv)
