(ns file-server.interfaces)

(defprotocol IStore
  "An abstract key value store that supports write, read, delete operations. Implementations of the protocol can be both local or remote."
  (write! [this key val])
  (read [this keys])
  (delete! [this keys]))

(defprotocol IDistributedFile
  "A protocol defining the DistributedFile type, an append-only file reference that allows manipulation of individual `chunks` of the file. 
  Each chunk's location and metadata is stored in a `manifest` map that can be used to re-assemble the file. Additionally, there is a property map used to store
  metadata about the file. The protocol is mostly agnostic regarding the implementation of the storage layer, but makes a few assumptions:

    * The purpose of `init!` is to setup any backing data stores or other state. It _should_ be called prior to any other members.
    * The File instance contains a property map of metadata for each file. This is useful for properties of the file that are not necessary 
        for peristing the chunk.
    * The `manifest` of a file is updated with the new chunk location when `add-chunk!` is invoked.

  The goal of the protocol is to decouple the storage layers for the 3 data components (chunk, metadata, manifest) to allow for each component to be
  implemented separately. This is ideal because it allows for different storage techniques based on the needs of the implementation."
  
  (init! [this options])
  (add-chunk! [this chunk-id start end chunk-data])
  (get-chunk [this chunk-id])
  (has-chunk? [this chunk-id])
  (get-manifest [this])
  (get-property [this key])
  (set-property [this key val])
  (delete! [this]))