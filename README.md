# Approach

Ghost protocol server built using http-kit and Google's leveldb (thanks to Factual for vending bindings).

The server accepts PUT requests at the route `/file/:id` with the header `Content-Range`. `File-Password` and `Content-Type` headers are optional. When the server receives a GET request, the file is returned iff the file exists and has never been accessed. Once a file is requested, the backing file is deleted but a metadata record remains. On any subsequent GET requests, a 410 response code is given to the client.

Simple password authorization is provided via a HTTP header "file-password" on both upload and retrieval.

Some caveats:

* Re-sending a chunk with a known starting byte will simply no-op and respond with 200. 
* Reuploading files after deletion is not supported. (The chunks will simply no-op if the same byte ranges are used. This is because I don't delete the manifest when a file is deleted.)
* Individual requests can change the chunk range, so long as the chunk ranges do not overlap and the content-range header is accurate. 
* Incorrect chunk ranges will lead to data corruption.

`

## Usage

### Server
FIXME: explanation

    $ java -jar vimeo-upload-0.1.0-standalone.jar [args]

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
<

## Improvements



