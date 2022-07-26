package sh.hella.http.example;

import sh.hella.http.HttpServer;
import sh.hella.http.Response;

import java.nio.charset.StandardCharsets;

public class ServerTest {
    public static void main(String[] args) {
        Response response = Response.builder()
            .status(200)
            .header("Content-Type", "text/plain")
            .body("Hello, world!".getBytes(StandardCharsets.UTF_8))
            .build();

        new HttpServer(request -> response)
            .start()
            .join();
    }
}
