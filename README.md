# Overview 

Simple file server built using http-kit and Google's leveldb (thanks to Factual for vending bindings). Project for a potential employer, used to learn Clojure. 

The server has only two routes:

 * `PUT /file/:id` -- requires `Content-Range` header, optional headers are `File-Password`, `Content-Type`
 * `GET /file/:id` -- optional `File-Password`

The server accepts PUT requests at the route `/file/:id` with the header `Content-Range`. `File-Password` and `Content-Type` headers are optional. When the server receives a GET request, the file is returned iff the file exists and has never been accessed. Once a file is requested, the backing file is deleted but a metadata record remains. On any subsequent GET requests, a 410 response code is given to the client.

Simple password authorization is provided via a HTTP header `file-password` on both upload and retrieval. Incorrect passwords will return 404s.

The API has some limitations, see [improvements and limitations](https://github.com/ben-mays/file-server#improvements--limitations).

# Development / Architecture

I spent a few hours prototyping different approaches and went forward with a design centered around indepedent storage for each chunk and a consistent centralized manifest to place them back together. This allows the storage system to abstract away placement of chunks from the application and place them in the most appropriate place based on implementation. My main goals (beyond the simple protocol) were to support extremely large file uploads and upload files as quickly as possible. In this approach, files can be uploaded in parallel, to/from different servers, with the manifest being the only point of contention. Because the manifest is append-only, writes to the manifest _should_ be extremely quick and consistent. 

## Clojure

I started the project using Clojure, a language I wasn't too familiar with, but had written a few CLI tools in. To someone not familiar with it, it can appear cryptic. It's interop with Java has led to some neglect regarding the type system (`defrecord`, `deftype`) - and most Clojure projects also include a Java source for defining classes and interfaces.

In retrospect, I wouldn't choose Clojure again. I chose it mainly to become more familiar with it and was partially inspired by [Soundcloud's use of Clojure for web services] (http://blog.josephwilk.net/clojure/building-clojure-services-at-scale.html). 

Here is a overview of each file:

* `/core.clj` - The entry point for the JVM, it:
   * Sets up the LevelDBStore and passes them to the GhostFile class to be used statically.
   * Configures the routes and binds them to their handler functions
   * Sets up instrumentation, loggers and other config (removed for submission)
	
* `/interfaces.clj` - Defines interfaces (protocols in Clojure) for the Store and DistributedFile types

* `/file/ghost_file.clj` - An implementation of the DistributedFile protocol that uses a Store implementation to                               construct two stores, 'metadata-store' and 'chunk-store'.

* `/store/leveldb_store.clj` - An implementation of the Store protocol that uses LevelDB.

* `/handler/upload.clj` - Takes a Request object and uses the DistributedFile API to upload files (and chunks) into the system.

* `/handler/retrieve.clj` - Takes a Request object and uses the DistributedFile API to retrieve files.

# Usage

## Server

To start the server, you can simply run `server.sh` in the `scripts` directory. The server takes two optional positional arguments: `server.sh PORT DB-ROOT`

For example:

```
✔ file-server
$ cd scripts
✔ file-server/scripts
$ ./server.sh
Starting server on port 8080, using /tmp/ for database root.
```

## Clients

Clients can be implemented by simply using the proper headers. Example cURL requests are provided below. The server doesn't support [chunked transfer encoding](https://en.wikipedia.org/wiki/Chunked_transfer_encoding), and the body of the request is a raw byte array. Clients will have to process responses synchronously or parse the `Content-Range` header in the response to place the chunk accurately.

### Java / Bash 

In the `bin` directory, there is a JVM client that supports upload files and it is accessbile via `scripts/upload-file.sh`.

```
✔ file-server/scripts
$ ./upload-file.sh -h
Ghost Protocol Upload Client!

A primitive multithreaded client that can upload multiple files in parallel. Each file is read from disk and uploaded sequentially, within the thread.

Usage: ./upload-file.sh -e 'http://localhost:8080' [options] file1 file2 ... fileN

Note: Some options don't make sense given multiple files.

Options:
  -h, --help                       Prints the help
  -e, --endpoint ENDPOINT          Required. The server endpoint to send requests to, does not include the routes or trailing /.
  -c, --content-type CONTENT-TYPE  Content-type header for the file to upload. When uploading multiple files,
          all files will have the same content-type. Defaults to 'video/mp4'.
  -i, --id ID                      The identifier to use to upload the file under. Defaults to the filename. Do not use if uploading multiple files.
  -p, --password PASSWORD          A password to use for file retrieval. When uploading multiple files, all files will have the same password.
  
```

## Examples

### Using cURL

Below is a quick example of the headers sent and received, which is useful for writing a client.

```
curl -v 'http://localhost:8081/file/test.txt' -X PUT \
	-H "file-password: TEST" \
	-H "Content-Type: text/plain" \
	-H "Content-Range: bytes 0-14/28" \
	-d "<your chunk 1>"

> PUT /file/test.txt HTTP/1.1
> Host: localhost:8081
> User-Agent: curl/7.43.0
> Accept: */*
> file-password: TEST
> Content-Type: text/plain
> Content-Range: bytes 0-14/28
> Content-Length: 14
>
< HTTP/1.1 200 OK
< Content-Range: 0-14/28
< Content-Length: 7
< Server: http-kit
< Date: Mon, 19 Oct 2015 19:50:37 GMT
0-14/28
```

Note the body also contains the range uploaded. This is useful for writing quick clients that don't need to parse headers.

```
curl -v 'http://localhost:8081/file/test.txt' -X PUT \
	-H "file-password: TEST" \ 
	-H "Content-Type: text/plain" \
	-H "Content-Range: bytes 15-28/28" \
	-d "<your chunk 2>"

> PUT /file/test.txt HTTP/1.1
> Host: localhost:8081
> User-Agent: curl/7.43.0
> Accept: */*
> file-password: TEST
> Content-Type: text/plain
> Content-Range: bytes 15-28/28
> Content-Length: 14
>
< HTTP/1.1 200 OK
< Content-Range: 15-28/28
< Content-Length: 8
< Server: http-kit
< Date: Mon, 19 Oct 2015 19:50:46 GMT
<
15-28/28
```

Now we can retrieve the file:

```
curl -v 'http://localhost:8081/file/test.txt' -X GET -H "file-password: TEST"
*   Trying ::1...
* Connected to localhost (::1) port 8081 (#0)
> GET /file/test.txt HTTP/1.1
> Host: localhost:8081
> User-Agent: curl/7.43.0
> Accept: */*
> file-password: TEST
>
< HTTP/1.1 200 OK
< Content-Type: text/plain
< Content-Range: 0-14/*
< Transfer-Encoding: chunked
< Server: http-kit
< Date: Mon, 19 Oct 2015 19:51:15 GMT
<
<your chunk 1><your chunk 2>
```

And a quick test to show the file is removed:

```
curl -v 'http://localhost:8081/file/test.txt' -X GET -H "file-password: TEST"
> GET /file/test.txt HTTP/1.1
> Host: localhost:8081
> User-Agent: curl/7.43.0
> Accept: */*
> file-password: TEST
>
< HTTP/1.1 410 Gone
< Content-Length: 0
< Server: http-kit
< Date: Mon, 19 Oct 2015 20:00:37 GMT
```
# Improvements / Limitations

* Re-sending a chunk with a known starting byte will simply no-op and respond with 200. (This is kind of like retryable uploads, as the server doesn't re-process chunks. A separate API could be vended to give the client the chunk ranges missing.)
* Reuploading files after retrieval is not supported and will return a 400 response.
* Individual chunk upload requests can change the chunk range, so long as the chunk ranges do not overlap and the content-range header is accurate. 
* Passing incorrect chunk ranges will lead to data corruption.
* A client _can_ read a file at anytime during the upload, causing the existing chunks to be deleted and the file to become inaccessible.

# Misc

There is a `verify-upload.sh` script that uploads a video, retrieves it and compares their checksums. This was useful for regression testing, identifying corruption and testing different file types.
Here is an example:

[![asciicast](https://asciinema.org/a/24j9wkdnu6fj8m0a3ilbes2ph.png)](https://asciinema.org/a/24j9wkdnu6fj8m0a3ilbes2ph?t=12)

