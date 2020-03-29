import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {

    private final int bufferSize = 4;

    private ServerSocket serverSocket;
    private Selector selector;

    private volatile boolean running = true;
    private int clientCount = 0;

    public static void main(String[] args) {
        final Server myServer = new Server(8000);

        Thread closeThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            int exitCode;
            do {
                exitCode = scanner.nextInt();
            } while (exitCode != 0);
            myServer.closeServer();
            scanner.close();
        });
        closeThread.start();
        myServer.listen();
    }

    public Server(int port) {
        try {
            init(port);
            running = true;
            System.out.println("Server started\nPrint 0 to close server");
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private void init(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocket = serverSocketChannel.socket();
        serverSocket.bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void listen() {
        while (running) {
            try {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid())
                        continue;
                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                }

            } catch (IOException io) {
                io.printStackTrace();
            }
        }
    }

    public void write(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Attachment clientAttachment = (Attachment) key.attachment();

        clientAttachment.clientBuffer.flip();
        clientChannel.write(clientAttachment.clientBuffer);
        if (clientAttachment.clientBuffer.hasRemaining()) {
            clientAttachment.clientBuffer.compact();
        } else {
            clientAttachment.clientBuffer.clear();
        }
    }

    public void read(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Attachment clientAttachment = (Attachment) key.attachment();

        long numberOfBytes = clientChannel.read(clientAttachment.clientBuffer);
        if (numberOfBytes == -1) {
            clientCount--;
            System.out.println("CLIENT #" + clientAttachment.clientNumber + " DISCONNECTED");
            clientChannel.socket().close();
            return;
        }

        int oldLimit = clientAttachment.clientBuffer.limit();
        int oldPosition = clientAttachment.clientBuffer.position();

        clientAttachment.clientBuffer.flip();
        System.out.print(new String(clientAttachment.clientBuffer.array(),
                clientAttachment.clientBuffer.position(), clientAttachment.clientBuffer.remaining()));

        clientAttachment.clientBuffer.limit(oldLimit).position(oldPosition);
    }

    public void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientCount++;
        Attachment clientAttachment = new Attachment(clientCount);
        clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, clientAttachment);
        System.out.println("CLIENT #" + clientCount + " CONNECTED");
    }

    public void closeServer() {
        try {
            running = false;
            serverSocket.close();
            System.out.println("Server is closing");
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    class Attachment {
        ByteBuffer clientBuffer;
        int clientNumber;

        Attachment(int number) {
            clientBuffer = ByteBuffer.allocate(bufferSize);
            this.clientNumber = number;
        }
    }
}
