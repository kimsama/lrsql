(ns lrsql.ops.query.auth
  (:require [lrsql.functions :as f]
            [lrsql.util.auth :as ua]))

(defn query-credential-scopes*
  [tx input]
  (some->> (f/query-credential-scopes tx input)
           (map :scope)
           not-empty))

(defn query-credential-scopes
  "Query an API key and its secret key and return a result map containing
   the scope and auth key map. If the credentials are not found, return a
   sentinel to indicate that the webserver will return 401 Forbidden."
  [tx input]
  (if-some [scopes (query-credential-scopes* tx input)]
    ;; Credentials found - return result map
    (let [{:keys [api-key secret-key]}
          input
          scope-set (if (every? nil? scopes)
                      ;; Credentials not associated with any scope.
                      ;; The LRS MUST assume a requested scope of
                      ;; "statements/write" and "statements/read/mine"
                      ;; if no scope is specified.
                      #{:scopes/statements.write
                        :scopes/statements.read.mine}
                      ;; Return scope set
                      (->> scopes
                           (filter some?)
                           (map ua/scope-str->kw)
                           (into #{})))]
      {:result {:scopes scope-set
                :prefix ""
                :auth   {:username api-key
                         :password secret-key}}})
    ;; Credentials not found - uh oh!
    {:result :com.yetanalytics.lrs.auth/forbidden}))

(defn query-credentials
  [tx input]
  (let [creds  (->> input
                    (f/query-credentials tx)
                    (map (fn [{ak :api_key sk :secret_key}]
                           {:api-key ak :secret-key sk})))
        scopes (doall (map (fn [cred]
                             (->> cred
                                  (f/query-credential-scopes tx)
                                  (map :scope)))
                           creds))]
    (mapv (fn [cred cred-scopes]
            (assoc cred :scopes (set cred-scopes)))
          creds
          scopes)))
