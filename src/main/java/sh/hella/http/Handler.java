package sh.hella.http;

public interface Handler {
    Response handle(Request request);
}
