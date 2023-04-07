# hella-http

`hella-http` is a hella fast HTTP library for Java that uses [nio_uring](https://github.com/bbeaupain/nio_uring) for I/O. Although it is currently only around 300 lines of code, it is one of the most scalable HTTP servers available for Java today.

Feedback, suggestions, and contributions are most welcome!

## Requirements
* Linux >= 5.1
* Java >= 8

For both of these, the higher the version the better - free performance!

## Maven Usage

Hella will be published to Maven central once the API has stabilized.

## Hello World Example

```java
public static void main(String[] args) {
    byte[] body = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    String bodyLength = body.length + "";

    Response response = Response.builder()
        .status(200)
        .header("Content-Type", "text/plain")
        .header("Content-Length", bodyLength)
        .body(body)
        .build();

    new HttpServer(request -> response)
        .start()
        .join();
}
```

## Benchmarks

Hella scales exceptionally well with large amounts of clients. The following benchmarks were conducted between two EC2 c5.2xlarge instances, which have 8 virtual CPUs and 16GB of RAM. The library used to conduct and measure the benchmarks was `wrk`.

### Requests per Second

![Requests per second benchmark](https://github.com/bbeaupain/hella-http/blob/main/docs/requests.png?raw=true)

X axis: number of clients

Y axis: requests per second

### Latency (microseconds)

![Requests per second benchmark](https://github.com/bbeaupain/hella-http/blob/main/docs/latency.png?raw=true)

X axis: number of clients

Y axis: latency in microseconds

## License

MIT. Have fun and make cool things!
