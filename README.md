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

|                    | 1     | 10    | 100    | 1000   | 10000  | 25000  |
|--------------------|-------|-------|--------|--------|--------|--------|
| hella              | 12034 | 98312 | 411585 | 614449 | 628288 | 591419 |
| microhttp          | 11886 | 98925 | 286428 | 588984 | 372675 | 323757 |
| netty              | 11303 | 94751 | 364214 | 410423 | 319265 | 279239 |
| rapidoid-http-fast | 11574 | 97124 | 408889 | 531682 | 332036 | 236667 |

### Latency (microseconds)

![Requests per second benchmark](https://github.com/bbeaupain/hella-http/blob/main/docs/latency.png?raw=true)

|                    | 1   | 10  | 100 | 1000 | 10000 | 25000  |
|--------------------|-----|-----|-----|------|-------|--------|
| hella              | 83  | 100 | 231 | 1970 | 2690  | 3240   |
| microhttp          | 84  | 101 | 347 | 3002 | 27000 | 77250  |
| netty              | 108 | 133 | 423 | 5180 | 30950 | 84550  |
| rapidoid-http-fast | 93  | 138 | 291 | 2240 | 33280 | 105280 |

### Memory Usage (megabytes)

![Requests per second benchmark](https://github.com/bbeaupain/hella-http/blob/main/docs/memory.png?raw=true)

|                    | 1   | 10  | 100 | 1000 | 10000 | 25000  |
|--------------------|-----|-----|-----|------|-------|--------|
| hella              | 201 | 220 | 220 | 408  | 1400  | 3100   |
| microhttp          | 220 | 230 | 236 | 305  | 555   | 1200   |
| netty              | 238 | 295 | 298 | 321  | 463   | 800    |
| rapidoid-http-fast | 150 | 268 | 310 | 715  | 4600  | 10800  |

## License

MIT. Have fun and make cool things!
