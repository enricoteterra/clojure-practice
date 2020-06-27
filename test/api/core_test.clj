(ns api.core-test
  (:require [clojure.test :refer :all]
            [api.core :refer [in-memory-event-store apply-event]]))

(deftest in-memory-event-store-test
  (testing "it should keep a record of events"
    (let [{:keys [send, events]} (in-memory-event-store)]
      (is (empty? (events)))
      (send {:event/name "first event"})
      (is (= 1 (count (events))))
      (send {:event/name "another event"})
      (is (events) [{:event/name "first event"}
                    {:event/name "another event"}])))

  (testing "it should not share state"
    (is (empty? ((:events (in-memory-event-store)))))))

(deftest apply-event-test
  (testing "apply unknown or invalid events does not affect state"
    (is (empty? (apply-event [] {:event/name "some event"})))
    (is (empty? (apply-event [] {:event/name "task-added"
                                 :some-prop "some task"}))))

  (testing "`task-added` event is applied"
    (is [{:task/title "some task"}]
        (apply-event [] {:event/name "task-added"
                         :task/title "some task"})))

  (testing "`task-completed` event is applied"
    (is (empty? (-> [] (apply-event {:event/name "task-completed"
                                     :task/uri "todo/task/1"
                                     :task/title "a task"}))))

    (is (empty? (-> []
                    (apply-event {:event/name "task-added"
                                  :task/uri "todo/task/1"
                                  :task/title "a task"})
                    (apply-event {:event/name "task-completed"
                                  :task/uri "todo/task/1"
                                  :task/title "a task"}))))

    (is (= 1 (count (-> []
                        (apply-event {:event/name "task-added"
                                      :task/uri "todo/task/1"
                                      :task/title "a task"})
                        (apply-event {:event/name "task-completed"
                                      :task/uri "todo/task/2"
                                      :task/title "a task"})))))))

