(ns user
  (:require [openadr3.api :as api]
            [openadr3.mdns :as mdns]
            [babashka.process :refer [shell]]
            [clojure.string :as str]))

;; OpenAPI spec (via clj-oa3 dependency)
(def specfile "resources/openadr3-specification/3.1.0/openadr3.yaml")
(def vtn-url api/default-vtn-url)

;; ---------------------------------------------------------------------------
;; tmux-based service management
;; ---------------------------------------------------------------------------

(defn find-tmux-cmd []
  (-> (shell {:out :string} "which tmux") :out str/trim))

(defn restart-mqtt
  "Restart Mosquitto via brew services."
  []
  (let [{:keys [exit out err]} (shell {:out :string :err :string}
                                      "brew services restart mosquitto")]
    (println "Restarted Mosquitto, Exit:" exit "\nErr:" err "\nOut:" out)))

(defn kill-tmux-session [session-name]
  (let [{:keys [exit out err]} (shell {:out :string :err :string}
                                      "tmux kill-session" "-t" session-name)]
    (case exit
      0 (println "tmux session" session-name "killed")
      (println "tmux session" session-name "kill failed\nExit:" exit "\nErr:" err "\nOut:" out))))

(defn run-vtn-ri []
  (let [branch "dcj/issue-164"
        session-name "vtn-ri"
        cwd (System/getProperty "user.dir")
        homedir (System/getProperty "user.home")
        process-dir (str homedir "/projects/OpenADR/repos/openadr3-vtn-reference-implementation")
        {:keys [exit out err]} (shell {:out :string :err :string
                                       :extra-env {"VTN_RI_BRANCH" branch
                                                   "VTN_RI_DIR" process-dir}}
                                      (find-tmux-cmd) "new-session" "-d"
                                      "-s" session-name "-c" process-dir
                                      "-e" (str "VTN_RI_BRANCH=" branch)
                                      (str cwd "/scripts/run-vtn-ri"))]
    (case exit
      0 (println "vtn-ri started, to attach: tmux attach-session -t vtn-ri")
      (println "Exit:" exit "\nErr:" err "\nOut:" out))))

(defn kill-vtn-ri [] (kill-tmux-session "vtn-ri"))

(defn run-vtn-callback-svc []
  (let [branch "main"
        session-name "vtn-callbk-svc"
        cwd (System/getProperty "user.dir")
        homedir (System/getProperty "user.home")
        process-dir (str homedir "/projects/OpenADR/repos/test-callback-service")
        {:keys [exit out err]} (shell {:out :string :err :string
                                       :extra-env {"VTN_CBS_BRANCH" branch
                                                   "VTN_CBS_DIR" process-dir}}
                                      (find-tmux-cmd) "new-session" "-d"
                                      "-s" session-name "-c" process-dir
                                      "-e" (str "VTN_CBS_BRANCH=" branch)
                                      (str cwd "/scripts/run-vtn-cback-svc"))]
    (case exit
      0 (println "vtn-callbk-svc started, to attach: tmux attach-session -t vtn-callbk-svc")
      (println "Exit:" exit "\nErr:" err "\nOut:" out))))

(defn kill-vtn-callback-svc [] (kill-tmux-session "vtn-callbk-svc"))

(comment
  ;; Create clients
  (def ven (api/create-ven-client specfile "ven_token" vtn-url))
  (def bl (api/create-bl-client specfile "bl_token" vtn-url))

  ;; mDNS discovery
  (mdns/discovery mdns/mdns-instance mdns/hosts)
  @mdns/hosts

  ;; Service management
  (run-vtn-ri)
  (kill-vtn-ri)
  (run-vtn-callback-svc)
  (kill-vtn-callback-svc))
