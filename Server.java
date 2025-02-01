import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

class UniqueIdGenerator implements Serializable{
    private long counter = 0;
    private Queue<Long> availableIds = new LinkedList<>();

    public UniqueIdGenerator(long counter,Queue<Long> availableIds){
        this.counter = counter;
        this.availableIds =availableIds;
    }

    public long generateId(){
        Long Id = availableIds.poll();
        if( Id == null ){
            return counter++;
        }
        return Id;
    }

    public void releaseId(Long Id) throws Exception {
        if( availableIds.contains(Id) ){
            throw new Exception("Id already released");
        }
        availableIds.add(Id);
    }
}

class Buffer{
    public int currBufferSize = 0;
}

class ClientData implements Serializable{
    public Set<String> publicFiles = new HashSet<>();
    public Set<String> privateFiles = new HashSet<>();

    public Queue<FileRequest> fileRequestsInbox = new LinkedList<>();
    public Queue<FileRequest> responsesInbox = new LinkedList<>();
}

public class Server {

    public HashMap<String, NetworkUtil> clientNetworkMap = new HashMap<>();
    public HashMap<String, ClientData> clientDataMap = null;
    public final HashMap<Long,UploadFileRequest> filesReceiving = new HashMap<>();

    public final UniqueIdGenerator fileIdGenerator = new UniqueIdGenerator(0,new LinkedList<>());
    public UniqueIdGenerator reqIdGenerator = null;
    public HashMap<Long,FileRequest> fileRequests = null;

    public int maxChunkSize = 64000; //100KB
    public int minChunkSize = 5000; // 5KB
    public int maxBufferSize = 1000000000;
    public final Buffer buffer = new Buffer();
    public String databasePath = "Server/database.dat";

    Server() {

        createDirectory("Server");
        readFromFile(databasePath);
        if(clientDataMap == null){
            clientDataMap = new HashMap<>();
        }
        if( fileRequests == null ){
            fileRequests = new HashMap<>();
        }
        if( reqIdGenerator == null )
            reqIdGenerator = new UniqueIdGenerator(0,new LinkedList<>());

        try {
            ServerSocket serverSocket = new ServerSocket(33333);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    Thread thread = new Thread(() -> {
                        try {
                            serve(clientSocket);
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    });

                    thread.start();
                }catch (Exception e) {
                    System.out.println(e);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private boolean createDirectory(String directoryPath){
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created){
                System.out.println("Failed to create the directory.");
                return false;
            }
        }
        return true;
    }

    public void writeToFile( String filePath) {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(filePath))) {
            outputStream.writeObject(clientDataMap);
            outputStream.writeObject(fileRequests);
            outputStream.writeObject(reqIdGenerator);
//            System.out.println("HashMap has been written to the file successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("An error occurred while writing HashMap to the file: " + e.getMessage());
        }
    }

    private void readFromFile(String filePath) {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(filePath))) {
            clientDataMap = (HashMap<String, ClientData>) inputStream.readObject();
            fileRequests = (HashMap<Long,FileRequest>) inputStream.readObject();
            reqIdGenerator = (UniqueIdGenerator) inputStream.readObject();
//            System.out.println("HashMap has been read from the file successfully.");
        } catch (FileNotFoundException fe){
        }
        catch (IOException | ClassNotFoundException e) {
            System.out.println("An error occurred while reading HashMap from the file: " + e.getMessage());
        }
    }

    private void serve(Socket clientSocket) throws IOException, ClassNotFoundException {
        NetworkUtil networkUtil = new NetworkUtil(clientSocket);
        String clientName = (String) networkUtil.read();
//        System.out.println(clientName + " " + clientSocket.getPort() + " " + clientSocket.getInetAddress().getHostAddress());

        synchronized (clientDataMap){
            if( !clientDataMap.containsKey(clientName) ){
                createDirectory("Server/clientFiles/"+clientName+"/public");
                createDirectory("Server/clientFiles/"+clientName+"/private");
                clientDataMap.put(clientName,new ClientData());
                writeToFile(databasePath);
            }else if( clientNetworkMap.containsKey(clientName) ){
                networkUtil.write("There is already a client connected with this username");
                networkUtil.closeConnection();
                return;
            }
            clientNetworkMap.put(clientName, networkUtil);
        }

        System.out.println(clientName + " is online");
        networkUtil.write("success");
        new ReadThreadServer(networkUtil,clientName,this);
    }

    public static void main(String args[]) {
        Server server = new Server();
    }
}
