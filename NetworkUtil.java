import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class NetworkUtil {
    public Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    public NetworkUtil(String s, int port) throws IOException {
        this.socket = new Socket(s, port);
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream());
    }

    public NetworkUtil(Socket s) throws IOException {
        this.socket = s;
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream());
    }

    public Object read() throws IOException, ClassNotFoundException {
        return ois.readUnshared();
    }

    public void write(Object o) throws IOException {
        oos.writeUnshared(o);
    }

    public void writeFlush() throws IOException {
        oos.flush();
    }

    public void readFlush() throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        int prevTimeout = socket.getSoTimeout();
        socket.setSoTimeout(1000);
        InputStream inputStream = socket.getInputStream();
        try{
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Discard the read data
            }
        }catch (SocketTimeoutException se){

        }
        socket.setSoTimeout(prevTimeout);
    }

    public void closeConnection() throws IOException {
        ois.close();
        oos.close();
    }
}

