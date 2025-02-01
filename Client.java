import java.io.File;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public ClientState state = new ClientState(this);
    public NetworkUtil networkUtil;
//    public volatile Integer state = 0; // 0 -> normalState, 1 -> uploadReqState, 2-> uploadReqAckState
    public String clientName;
    public int chunkSize = 100000;

    private void createDirectory(String directoryPath){
        File directory = new File(directoryPath);

        // Check if the directory already exists
        if (!directory.exists()){
            // Create the directory
            boolean created = directory.mkdirs();
            if (!created) {
                System.out.println("Failed to create the directory.");
            }
        }
    }

    public Client(String serverAddress, int serverPort) {
        try {
            Scanner scanner = new Scanner(System.in);
            while(true){
                Socket socket = new Socket(serverAddress,serverPort);
                networkUtil = new NetworkUtil(socket);
                networkUtil.socket.setSoTimeout(60*1000);

                System.out.print("Enter name of the client: ");
                clientName = scanner.nextLine();
                networkUtil.write(clientName);

                Object o = networkUtil.read();
                if (o instanceof String) {
                    String msg = (String)o;
                    System.out.println("Server : " + (String) o);
                    if( msg.equals("success") ){
                        networkUtil.socket.setSoTimeout(1000);
                        createDirectory("Client/ClientFiles/"+ clientName );
                        new ReadThreadClient(networkUtil,this);
                        new WriteThreadClient(networkUtil,this);
                        break;
                    }
                    else if( !msg.equals("There is already a client connected with this username") )
                        throw new Exception("Unknown msg received");

                }else{
                    throw new Exception("Unknown object received");
                }

            }


        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
        String serverAddress = "127.0.0.1";
        int serverPort = 33333;
        Client client = new Client(serverAddress, serverPort);
    }
}

