(ns api.core
  (:require [clojure.spec.alpha :as s]
            [reitit.ring :as r]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec]
            [muuntaja.middleware :as mw]
            [ring.adapter.jetty :as j])
  (:gen-class))

(s/def :task/uri (s/and string? not-empty))
(s/def :task/title (s/and string? not-empty))
(s/def ::task (s/keys :req [:task/uri :task/title]))

(s/def :event/name (s/and string? not-empty))
(s/def ::event (s/keys :req [:event/name]))
(s/def :app/task-event (s/merge ::event ::task))

(s/def :error/reason (s/and string? not-empty))
(s/def :error/fault (s/and string? not-empty))
(s/def :error/details any?)
(s/def :app/system-error (s/keys :req [:error/reason :error/fault :error/details]))

;;; --------------------------- state reducer --------------------------------

(defn apply-event
  "Reduces a valid application state from a vector of task events. The
  events must be sorted in order of when the events were received."
  [tasks event]
  (case (:event/name event)
    "task-added"
    (->> (select-keys event [:task/uri :task/title])
         (conj (remove #(= (:task/uri event) (:task/uri %)) tasks)))
    "task-completed"
    (remove #(= (:task/uri %) (:task/uri event)) tasks)
    ;;; else
    (throw (ex-info "cannot apply event" event))))

;;; --------------------------- HTTP server ----------------------------------

(defn handle-post-event
  "Sends the received event to the event-store and responds to the request."
  [event-name send-to-store]
  (fn [request]
    (let [event {:event/name event-name
                 :task/uri   (get-in request [:body-params :uri])
                 :task/title (get-in request [:body-params :title])}]
      (when (s/valid? :app/task-event event) (send-to-store event)))
    {:status 201}))

(defn snapshot-from [events-from-store]
  (fn [_]
    (try
      {:status 200 :body (reduce apply-event [] (events-from-store))}
      (catch clojure.lang.ExceptionInfo _ {:status 500}))))

(defn app
  "Listens to HTTP requests and routes them to the correct handlers.

  `event-store` is a map of:
   - a :send function. It receives an event (map with at least :event/name
   and any other keywords related to the event).
   - an :events function. It responds with the list of events in the store."
  [event-store]
  (let [{send-to-store :send stored-events :events} event-store]
    (r/ring-handler
      (r/router
        [["/tasks/added"
          {:post       (handle-post-event "task-added" send-to-store)
           :parameters {:body {:uri :task/uri :title :task/title}}}]
         ["/tasks/completed"
          {:post       (handle-post-event "task-completed" send-to-store)
           :parameters {:body {:uri :task/uri :title :task/title}}}]
         ["/tasks" {:get (snapshot-from stored-events)}]]
        {:data
         {:coercion   reitit.coercion.spec/coercion
          :middleware [mw/wrap-format
                       rrc/coerce-exceptions-middleware
                       rrc/coerce-request-middleware
                       rrc/coerce-response-middleware]}})
      (r/create-default-handler))))

(defn in-memory-event-store []
  (let [events (atom [])]
    {:send   (fn [event]
               (when (s/valid? ::event event)
                 (swap! events conj event)))
     :events (fn [] @events)}))

(defn -main [& args] (j/run-jetty (app (in-memory-event-store)) {:port 3000}))
