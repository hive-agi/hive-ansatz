(ns hive-ansatz.ports
  "DDD ports for the hive-ansatz bounded context (ISP: three narrow
   protocols; one adapter record may satisfy several).

   Declarations are OPAQUE values end-to-end: core namespaces never touch
   prover-kernel classes — introspection goes through IDeclInfo, effects
   through IProofEnv/IDeclCodec. The concrete prover (ansatz, or anything
   else with a kernel-checkable decl model) is injected (DIP).")

(defprotocol IProofEnv
  "Effectful surface of a live prover environment."
  (overlay [this]
    "Sequence of opaque decls added on top of the backing store
     (the session's local additions). Never includes the store itself.")
  (present? [this decl-name]
    "True when `decl-name` (string) resolves in the environment.")
  (add-decl! [this decl]
    "Add an opaque decl to the environment. Returns nil.")
  (verify [this decl]
    "Kernel-check the decl's proof term against its type. Returns boolean."))

(defprotocol IDeclInfo
  "Pure introspection over opaque decls."
  (decl-name [this decl]
    "The decl's name as a string.")
  (theorem? [this decl]
    "True when the decl carries a kernel-checkable proof term."))

(defprotocol IDeclCodec
  "Serialization of opaque decls to/from a durable snapshot file."
  (write-decls! [this path decls]
    "Write decls to `path`. Returns the number written.")
  (read-decls [this path]
    "Read decls from `path`. Returns {:header map :decls [decl ...]}."))
