(ns hey-friend.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [hey-friend.core-test]))

(enable-console-print!)

(doo-tests 'hey-friend.core-test)
