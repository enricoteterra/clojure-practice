(ns api.core
  (:require [clojure.spec.alpha :as s])
  (:gen-class))

(s/def :event/name (s/and string? seq))
(s/def :task/uri (s/and string? seq))
(s/def :task/title (s/and string? seq))
(s/def ::task-event (s/keys :req [:event/name :task/uri :task/title]))

(defn apply-event [tasks event]
  (if (s/invalid? (s/conform ::task-event event)) tasks
      (case (:event/name event)
        "task-added" (conj tasks (select-keys event [:task/uri :task/title]))
        "task-completed" (remove #(= (:task/uri %) (:task/uri  event)) tasks)
        :else tasks)))

(defn in-memory-event-store []
  (let [events (atom [])]
    {:send (fn [event] (swap! events conj event))
     :events (fn [] @events)}))

(defn app [event-store])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; (app in-memory-event-store))
  (println "Hello, World!"))
