# vimeo-upload

Ghost protocol server built using http-kit and Google's leveldb (thanks to Factual for vending bindings).

The server is built to be fast and resilient. The server accepts PUT requests for file upload, which are stored as byte arrays in
leveldb. When the server receives a GET request, the file is returned iff the file exists and has never been accessed. Once a
file is requested, it is marked for deletion. On any subsequent GET requests, a 410 response code is given to the client.

Simple password authorization is provided via a HTTP header "upload-pw".

## Installation


## Usage

FIXME: explanation

    $ java -jar vimeo-upload-0.1.0-standalone.jar [args]

## Options


## Examples

...

### Bugs


