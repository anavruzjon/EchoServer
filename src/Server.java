import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class Server {

    private static final int BUFFER_SIZE = 200;
    private static final int END_OF_CHANNEL_STREAM = -1;
    private static final int SERVER_CLOSE_CODE = 0;
    private static final int EMPTY_CHANNEL_STREAM = 0;
    private static final int DEFAULT_PORT = 8000;
    private static final int EMPTY_SIZE = 0;

    private ServerSocket serverSocket;
    private Selector selector;

    private volatile boolean running = true;
    private int clientCount = 0;

    public static void main(String[] args) {
        int port;
        try {
            port = (args.length != EMPTY_SIZE) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            port = DEFAULT_PORT;
        }

        final Server myServer = new Server(port);

        Thread closeThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                int code;
                do {
                    code = scanner.nextInt();
                } while (code != SERVER_CLOSE_CODE);
            } finally {
                myServer.closeServer();
            }
        });
        closeThread.start();
        myServer.listen();

        try {
            closeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Server(int port) {
        try {
            selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            running = true;
            System.out.println("Server started\nPrint 0 to close server");
        } catch (IOException io) {
            io.printStackTrace();
        }
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
                        System.out.println("Reading...");
                        read(key);
                    } else if (key.isWritable()) {
                        System.out.println("Writing..");
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
        ByteBuffer outBuffer = clientAttachment.toByteBuffer();

        try {

            int writtenBytesCount = clientChannel.write(outBuffer);

            if (writtenBytesCount == clientAttachment.fillByteCount) {
                clientAttachment.reset();
                key.interestOps(SelectionKey.OP_READ);
            } else {
                clientAttachment.writeCount(writtenBytesCount);
                key.interestOps(SelectionKey.OP_WRITE);
            }
            outBuffer.clear();
        } catch (IOException e) {
            clientDisconnected(key);
        }
    }

    public void read(SelectionKey key) throws IOException {

        SocketChannel clientChannel = (SocketChannel) key.channel();
        Attachment clientAttachment = (Attachment) key.attachment();
        ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {

            int readBytes = clientChannel.read(readBuffer);
            System.out.println("Read bytes count: " + readBytes);
            if (readBytes == END_OF_CHANNEL_STREAM) {
                clientCount--;
                System.out.println("CLIENT #" + clientAttachment.clientNumber + " DISCONNECTED");
                System.out.println("CLIENT DATA: " + clientAttachment.clientMessage.toString());
                clientChannel.socket().close();
                key.cancel();
                return;
            }

            while (readBytes != EMPTY_CHANNEL_STREAM) {
                readBuffer.flip();
                String readString = new String(readBuffer.array(), readBuffer.position(), readBuffer.remaining());
                System.out.println(readString);
                clientAttachment.appendMessage(readString);
                int writtenBytes = clientChannel.write(readBuffer);
                if (writtenBytes != readBytes) {
                    byte[] remain = Arrays.copyOfRange(readBuffer.array(), writtenBytes, readBytes);
                    clientAttachment.fillBuffer(remain, readBytes - writtenBytes);
                    key.interestOps(SelectionKey.OP_WRITE);
                    break;
                }
                readBuffer.clear();
                readBytes = clientChannel.read(readBuffer);
            }

            key.interestOps(SelectionKey.OP_READ);

        } catch (IOException e) {
            clientDisconnected(key);
        }

    }

    public void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientCount++;
        Attachment clientAttachment = new Attachment(clientCount);
        clientChannel.register(selector, SelectionKey.OP_READ, clientAttachment);
        System.out.println("CLIENT #" + clientCount + " CONNECTED");
    }

    private void clientDisconnected(SelectionKey key) throws IOException {
        Attachment clientAttachment = (Attachment) key.attachment();
        SocketChannel clientChannel = (SocketChannel) key.channel();
        clientCount--;
        System.out.println("CLIENT #" + clientAttachment.clientNumber + " DISCONNECTED");
        System.out.println("CLIENT DATA: " + clientAttachment.clientMessage.toString());
        clientChannel.socket().close();
        key.cancel();
    }

    public void closeServer() {
        try {
            running = false;
            serverSocket.close();
            selector.wakeup();
            selector.close();
            System.out.println("Server is closing");
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    class Attachment {
        StringBuilder clientMessage;
        byte[] outBuffer = new byte[BUFFER_SIZE];
        int fillByteCount;
        int position;
        int clientNumber;

        Attachment(int number) {
            this.clientNumber = number;
            fillByteCount = 0;
            position = 0;
            clientMessage = new StringBuilder();
        }

        public void appendMessage(String readData) {
            clientMessage.append(readData);
        }

        public void reset() {
            fillByteCount = 0;
            position = 0;
        }

        public void writeCount(int writtenByteCount) {
            fillByteCount -= writtenByteCount;
            position += writtenByteCount;
        }

        public void fillBuffer(byte[] array, int len) {
            for (int i = position, l = 0; i < position + len; i++, l++)
                outBuffer[i] = array[l];
            fillByteCount += len;
        }

        public ByteBuffer toByteBuffer() {
            return ByteBuffer.wrap(outBuffer, position, fillByteCount);
        }
    }
}
