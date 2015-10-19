# Approach

## Overview 

Ghost protocol server built using http-kit and Google's leveldb (thanks to Factual for vending bindings).

The server accepts PUT requests at the route `/file/:id` with the header `Content-Range`. `File-Password` and `Content-Type` headers are optional. When the server receives a GET request, the file is returned iff the file exists and has never been accessed. Once a file is requested, the backing file is deleted but a metadata record remains. On any subsequent GET requests, a 410 response code is given to the client.

Simple password authorization is provided via a HTTP header "file-password" on both upload and retrieval. Incorrect passwords will return 404s.

Some caveats:

* Re-sending a chunk with a known starting byte will simply no-op and respond with 200. (This is kind of like retryable uploads, as the server doesn't re-process chunks. A separate API could be vended to give the client the chunk ranges missing.)
* Reuploading files after retrieval is not supported and will return a 400 response.
* Individual chunk upload requests can change the chunk range, so long as the chunk ranges do not overlap and the content-range header is accurate. 
* Passing incorrect chunk ranges will lead to data corruption.
* A client _can_ read a file at anytime during the upload, causing the existing chunks to be deleted and the file to become inaccessible.

## Development / Architecture

I spent a few hours prototyping different approaches and went forward with a design centered around indepedent storage for each chunk and a consistent centralized manifest to place them back together. This allows the storage system to abstract away placement of chunks from the application and place them in the most appropriate place based on implementation. My main goals (beyond the simple protocol) were to support extremely large file uploads and upload files as quickly as possible. In this approach, files can be uploaded in parallel, to/from different servers, with the manifest being the only point of contention. Because the manifest is append-only, writes to the manifest _should_ be extremely quick and consistent. 

The source here is a contrived example of the above approach, an attempt to abstract different layers into 'services'. The abstraction layers are roughly the Application (all the handlers, routing), Storage Server (DistributedFile, GhostFile), Peristence (Store, LevelDBStore).

I expanded on the services oriented approach [https://github.com/ben-mays/designs/blob/master/scalable-file-store/](in a rough design) that was out of scope for this project.

## Usage

### Server
FIXME: explanation

    $ java -jar file-server-0.1.0-standalone.jar [args]

### Clients


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
## Improvements



