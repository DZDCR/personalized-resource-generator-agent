import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class WsProbe {
    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "ws://127.0.0.1:8080";
        HttpClient client = HttpClient.newHttpClient();
        Probe a = new Probe("A");
        Probe b = new Probe("B");
        WebSocket wsA = client.newWebSocketBuilder()
                .buildAsync(URI.create(base + "/ws/im?studentId=1"), a).join();
        WebSocket wsB = client.newWebSocketBuilder()
                .buildAsync(URI.create(base + "/ws/im?studentId=2"), b).join();

        System.out.println("A connected: " + a.next());
        System.out.println("B connected: " + b.next());

        String clientMsgId = "ws-" + UUID.randomUUID();
        String frame = "{\"v\":1,\"type\":\"message.send\",\"requestId\":\"" + clientMsgId
                + "\",\"payload\":{\"conversationId\":1,\"clientMsgId\":\"" + clientMsgId
                + "\",\"content\":\"WebSocket 实时联调\"}}";
        wsB.sendText(frame, true).join();

        System.out.println("B ack: " + b.next());
        System.out.println("A new: " + a.next());

        wsA.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        wsB.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    static class Probe implements WebSocket.Listener {
        private final String name;
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        Probe(String name) {
            this.name = name;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messages.offer(data.toString());
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            messages.offer("CLOSE " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            messages.offer("ERROR " + error);
        }

        String next() throws InterruptedException {
            String value = messages.poll(8, TimeUnit.SECONDS);
            return value == null ? name + ": TIMEOUT" : value;
        }
    }
}
