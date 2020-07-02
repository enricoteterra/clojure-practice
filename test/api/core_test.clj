(ns api.core-test
  (:require [api.core :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :refer [generate]]
            [muuntaja.core :as m]))

(defn some-task-event [& [overwrites]]
  (into (generate (s/gen :app/task-event)) overwrites))

(deftest apply-event-test
  (testing "applying unknown event returns an error"
    (let [event {:event/name "some event"}]
      (is (thrown? clojure.lang.ExceptionInfo (apply-event [] event)))))

  (testing "`task-added` event is applied"
    (is (seq (apply-event [] (some-task-event {:event/name "task-added"})))))

  (testing "many `task-added` events with same uri are applied"
    (let [tasks (->> [(some-task-event
                        {:event/name "task-added"
                         :task/title "first event" :task/uri "uri-1"})
                      (some-task-event
                        {:event/name "task-added"
                         :task/title "second event" :task/uri "uri-1"})
                      (some-task-event
                        {:event/name "task-added"
                         :task/title "last event" :task/uri "uri-1"})]
                     (reduce apply-event []))]
      (is (= 1 (count tasks)))
      (is (= "last event" (:task/title (first tasks))))))

  (testing "`task-completed` event is applied"
    (is (empty? (->> (some-task-event {:event/name "task-completed"})
                     (apply-event []))))

    (is (empty? (->> [(some-task-event {:event/name "task-added"
                                        :task/uri   "1"})
                      (some-task-event {:event/name "task-completed"
                                        :task/uri   "1"})]
                     (reduce apply-event []))))

    (is (= 1 (->> [(some-task-event {:event/name "task-added"
                                     :task/uri   "1"})
                   (some-task-event {:event/name "task-completed"
                                     :task/uri   "2"})]
                  (reduce apply-event [])
                  (count))))

    (is (= 0 (->> [(some-task-event {:event/name "task-added"
                                     :task/uri   "1"})
                   (some-task-event {:event/name "task-completed"
                                     :task/uri   "1"})]
                  (reduce apply-event [])
                  (count))))))

(deftest in-memory-event-store-test
  (testing "it should keep a record of events"
    (let [{:keys [send, events]} (in-memory-event-store)]
      (is (empty? (events)))
      (send {:event/name "first event"})
      (is (= 1 (count (events))))
      (send {:event/name "another event"})
      (is (events) [{:event/name "first event"}
                    {:event/name "another event"}])))

  (testing "it should not store invalid events"
    (let [{:keys [send, events]} (in-memory-event-store)]
      (send {:some-prop "first event"})
      (is (= 0 (count (events))))))

  (testing "it should not share state with other stores"
    (is (empty? ((:events (in-memory-event-store)))))))

(defn parse-body [request] (m/decode "application/json" (:body request)))
(deftest http-routes-test
  (testing "it should respond to GET `/tasks`"
    (let [response ((app (in-memory-event-store))
                    {:request-method :get :uri "/tasks"})]
      (is (= 200 (:status response)))
      (is (= [] (parse-body response))))

    (let [bad-event-store (in-memory-event-store)]
      ((:send bad-event-store) {:event/name "bad event"})
      (is (= 500 (:status ((app bad-event-store)
                           {:request-method :get :uri "/tasks"}))))))

  (testing "it should respond to POST `/tasks/added`"
    (is (= 400 (-> {:request-method :post :uri "/tasks/added"}
                   ((app (in-memory-event-store)))
                   (:status))))

    (is (= 201 (-> {:request-method :post
                    :uri            "/tasks/added"
                    :body-params    {:uri "uri-1" :title "new task"}}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should respond to POST `/tasks/completed`"
    (is (= 400 (-> {:request-method :post, :uri "/tasks/completed"}
                   ((app (in-memory-event-store)))
                   (:status))))

    (is (= 201 (-> {:request-method :post
                    :uri            "/tasks/completed"
                    :body-params    {:uri "uri-1" :title "new task"}}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should return 404 when request uri unknown"
    (is (= 404 (-> {:request-method :get :uri "some-uri"}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should return 405 when request method is not allowed"
    (is (= 405 (-> {:request-method :post :uri "/tasks"}
                   ((app (in-memory-event-store)))
                   (:status))))))

(deftest app-features-test
  (testing "it should add tasks"
    (let [app (app (in-memory-event-store))]
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-1" :title "new task"}})
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-2" :title "second task"}})
      (is (= 2 (count (parse-body (app {:request-method :get :uri "/tasks"})))))))

  (testing "it should complete tasks"
    (let [app (app (in-memory-event-store))]
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-1" :title "new task"}})
      (is (= 1 (count (parse-body (app {:request-method :get :uri "/tasks"})))))
      (app {:request-method :post
            :uri            "/tasks/completed"
            :body-params    {:uri "uri-1" :title "new task"}})
      (is (= 0 (count (parse-body (app {:request-method :get :uri "/tasks"}))))))))