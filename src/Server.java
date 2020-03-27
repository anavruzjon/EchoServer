import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {

    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private volatile int clientCount = 0;

    public static void main(String[] args) {


        final Server myServer = new Server(8000);

        Thread closeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Scanner scanner = new Scanner(System.in);
                int exitCode;
                while (true) {
                    exitCode = scanner.nextInt();
                    if (exitCode == 0)
                        break;
                }
                myServer.closeServer();
                scanner.close();
            }
        });
        closeThread.start();

        myServer.listen();


    }

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started");
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public void listen() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            clientCount++;
                            System.out.println("Client connected #" + clientCount);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                            String line;

                            while ((line = reader.readLine()) != null) {
                                System.out.println(line);
                                writer.write(line);
                                writer.newLine();
                                writer.flush();
                            }

                            System.out.println("Client disconnected");
                            clientSocket.close();
                            reader.close();
                            writer.close();

                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    }
                });

                clientThread.start();

            } catch (IOException io) {
                io.printStackTrace();
            }
        }
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
}
