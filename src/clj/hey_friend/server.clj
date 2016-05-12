(ns hey-friend.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [cemerick.friend :as friend]
            [qarth.friend]
            [qarth.oauth :as oauth]
            [qarth.impl.google])
  (:gen-class))

(derive ::admin ::user)

(def admins #{"awole20@gmail.com"})

(defn credential-fn [id]
  (let [email (get-in id [:qarth.oauth/record :email])]
    (if (contains? admins email)
      (do
        (println "admin " email)
        (assoc id :roles [::admin]))
      (do
        (println "normal user " email)
        (assoc id :roles [::user])))))

(def conf {:type :google
           :callback (or (env :callback) "http://localhost:3449/login")
           :api-key (or (env :api-key) "")
           :api-secret (or (env :api-secret) "")})
(def service (oauth/build conf))

(def workflow
  (qarth.friend/oauth-workflow
   {:service service
    :login-failure-handler
    (fn [_] (ring.util.response/redirect
             "/login?exception=true"))}))

(defroutes routes
  (GET "/" request "open.")
  (GET "/authlink" req
       (friend/authorize
         #{::user}
         (let [id (-> req (qarth.friend/requestor service) oauth/id)
               email (get-in req [:session :cemerick.friend/identity :authentications :qarth.oauth/anonymous :qarth.oauth/record :email])
               friend-attributes (-> req (qarth.friend/auth-record))]
           (str "<html><body>Hello friend! Your unique user ID is "
                id
                "<br/> and email:"
                email
                "<br/> and friend attributes:"
                friend-attributes
                "</body></html>"))))
  (GET "/admin" request
       (friend/authorize #{::admin} "Only admins can see this page."))
  (friend/logout (ANY "/logout" request (ring.util.response/redirect "/"))))

(def http-handler
  (-> routes
      (friend/authenticate
       {:workflows [workflow] :auth-url "/login"
        :credential-fn credential-fn})
      (wrap-defaults site-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-server http-handler {:port port :join? false})))
