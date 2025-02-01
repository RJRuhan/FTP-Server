import java.io.*;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.Vector;

public class WriteThreadClient implements Runnable {

    private Thread thr;
    private NetworkUtil networkUtil;
    private Client client;
    private Scanner input;

    public WriteThreadClient(NetworkUtil networkUtil, Client client) {
        this.networkUtil = networkUtil;
        this.client = client;
        this.thr = new Thread(this);
        thr.start();
    }


    private boolean sendFile(int chunkSize,FileInputStream fileInputStream) throws IOException, ClassNotFoundException {

        DataOutputStream dataOutputStream = new DataOutputStream(networkUtil.socket.getOutputStream());
        byte[] buffer = new byte[chunkSize];
        int bytes = -1;
        int chunkIndx = 1;

        while ( (bytes=fileInputStream.read(buffer,0,chunkSize)) != -1 ){
            dataOutputStream.writeInt(1);
            System.out.println("sending chunk No : " + chunkIndx );
            dataOutputStream.write(buffer,0,bytes);

            Object receivedObject = null;
            try{
                receivedObject = networkUtil.read();
            }catch (SocketTimeoutException e){
                System.out.println("WriteThread : " + "server acknowledgement timeout...");
                System.out.println("Aborting sending file...");
                dataOutputStream.writeInt(-1);
                return false;
            }

            if ( receivedObject instanceof String ){
                String ack = (String) receivedObject;
                System.out.println("server acknowledgement received");
                if ( ack.equals("chunk received") ){
                    System.out.println("WriteThread : server received chunk");
                }else{
                    System.out.println("WriteThread : " + "server didnt receive chunk ...");
                    System.out.println("Aborting sending file...");
                    return false;
                }
            }
            chunkIndx++;
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }

        networkUtil.write("success");
        Object receivedObject = null;
        try{
            receivedObject = networkUtil.read();
            if ( receivedObject instanceof String ){
                String ack = (String) receivedObject;
                System.out.println("server acknowledgement received");
                if ( ack.equals("success") ){
                    System.out.println("WriteThread : file upload successful...");
                }else{
                    System.out.println("WriteThread : " + "file upload failed...");
                    System.out.println("Aborting sending file...");
                    return false;
                }
            }
        }catch (SocketTimeoutException e){
            System.out.println("WriteThread : " + "Didnt receive final confirmation message whether the file upload was successful or not");
            System.out.println("Please check manually whether file was uploaded or not...");
            return false;
        }

        return true;
    }

    private void sendFileUploadRequest(File file,String fileName,boolean isPublic,FileInputStream fileInputStream,long reqID ) throws IOException, ClassNotFoundException {

        networkUtil.write(new UploadFileRequest(file.length(),fileName,isPublic, client.clientName,reqID));
        Object receivedObject = networkUtil.read();
        UploadRequestAckMsg ackMsg = null;
        if( receivedObject instanceof UploadRequestAckMsg){
            ackMsg = (UploadRequestAckMsg) receivedObject;
            System.out.println("WriteThread : acknowledge message is received");
            if( ackMsg.confirm ){
                System.out.println("WriteThread : acknowledge message is received");
                System.out.println( "chunk Size : " + ackMsg.chunkSize + " file ID : " + ackMsg.fileID );
                System.out.println("WriteThread : sending file in chunks...");

                sendFile(ackMsg.chunkSize,fileInputStream);

            }else{
                System.out.println("WriteThread : " + ackMsg.msg);

            }
        }

    }

    private void sendFileUploadRequest(String fileName, int fileAccess, Long reqID) throws IOException, ClassNotFoundException {

        File file = new File("Client/ClientFiles/" + client.clientName + "/" + fileName);
        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(file);
        }catch ( FileNotFoundException e){
            System.out.println("No such file exists");
            return;
        }


//        System.out.println("WriteThread : upload request sent to readThread");
        client.state.makeReadThreadWaitReq();
        System.out.println("WriteThread : Sending file");

        networkUtil.readFlush();
        networkUtil.writeFlush();

        int prevTimeout = networkUtil.socket.getSoTimeout();
        networkUtil.socket.setSoTimeout(30*1000);

        // send file size
        System.out.println("WriteThread : Sending upload request with file Meta Data");
        System.out.println("fileName : " + fileName);
        System.out.println("fileSize : " + file.length());

        sendFileUploadRequest(file,fileName,fileAccess == 1,fileInputStream,reqID);


        networkUtil.readFlush();
        networkUtil.writeFlush();

        networkUtil.socket.setSoTimeout(prevTimeout);
        fileInputStream.close();

//        System.out.println("WriteThread : returning to normal state...");
        client.state.setNormalState();


    }

    private void lookup(int reqID) throws IOException, ClassNotFoundException {
        client.state.makeReadThreadWaitReq();

        networkUtil.readFlush();
        networkUtil.writeFlush();

        int prevTimeout = networkUtil.socket.getSoTimeout();
        networkUtil.socket.setSoTimeout(30*1000);

        networkUtil.write(new LookupRequest(reqID));

        try{
            Object receivedObject = networkUtil.read();

            if( reqID == 1 ){
                if( receivedObject instanceof Data){
                    Data data = (Data) receivedObject;
                    System.out.println("\nOffline Clients : ");
                    System.out.println("Total Number of Offline Clients : " + data.offlineClients.size());
                    int i = 1;
                    for ( String elem : data.offlineClients ){
                        System.out.println(i++ + "." + elem);
                    }
                    i = 1;
                    System.out.println("\nOnline Clients : ");
                    System.out.println("Total Number of Online Clients : " + data.onlineClients.size());
                    for ( String elem : data.onlineClients ){
                        System.out.println(i++ + "." + elem);
                    }
                }
            }
            else if( reqID == 2 ){
                if( receivedObject instanceof Data){
                    Data data = (Data) receivedObject;
                    int i = 1;

                    int publicFilesSize = data.publicFiles.size();
                    int privateFilesSize = data.privateFiles.size();

                    System.out.println("Public Files : ");
                    System.out.println("Total Number of public files : " + publicFilesSize);
                    for ( String elem : data.publicFiles ){
                        System.out.print("    ");
                        System.out.println(i++ + "." + elem);
                    }

                    System.out.println("Private Files : ");
                    System.out.println("Total Number of private files : " + privateFilesSize);
                    for ( String elem : data.privateFiles ){
                        System.out.print("    ");
                        System.out.println(i++ + "." + elem);
                    }



                    if( publicFilesSize + privateFilesSize > 0 ){
                        while (true){
                            try{
                                System.out.println("Do you want to download any file?");
                                System.out.print("Enter yes or no to continue : ");
                                String inp = input.nextLine();
                                if( inp.equalsIgnoreCase("yes") ){
                                    System.out.print("Please Select the file(index No) to download : ");
                                    int choice = Integer.parseInt(input.nextLine());
                                    if( choice < 1 || choice > ( publicFilesSize + privateFilesSize ) )
                                        throw new NumberFormatException();
                                    if ( choice <= publicFilesSize ){
                                        System.out.println(data.publicFiles.get(choice-1));
                                        boolean success = downloadFile(data.publicFiles.get(choice-1),client.clientName,true);
                                        if(!success)
                                            System.out.println("file download failed");
                                        break;
                                    }else{
                                        choice -= publicFilesSize;
                                        System.out.println(data.privateFiles.get(choice-1));
                                        boolean success = downloadFile(data.privateFiles.get(choice-1),client.clientName,false);
                                        if(!success)
                                            System.out.println("file download failed");
                                        break;
                                    }

                                }else if ( inp.equalsIgnoreCase("no") ){
                                    break;
                                }else
                                    throw new NumberFormatException();
                            }catch (NumberFormatException e){
                                System.out.println("Invalid Input");
                            }

                        }
                    }

                }
            }
            else if( reqID == 3 ){
                if( receivedObject instanceof Data ){
                    Data data = (Data) receivedObject;
                    int i = 1;
                    int totalFiles = 0;
                    Vector<String> clients = new Vector<>(data.clientPublicFilesMap.keySet());
                    clients.remove(this.client.clientName);
                    System.out.println("Total number of clients : " + clients.size());
                    for ( String client : clients ) {

                        System.out.println(i++ + ". Client Name : " + client);
                        int j = 1;

                        System.out.println("    Public Files:");
                        System.out.println("    Total Number of public files : " + data.clientPublicFilesMap.get(client).size());
                        for (String file : data.clientPublicFilesMap.get(client)) {
                            System.out.print("        ");
                            System.out.println(j++ + "." + file);
                            totalFiles++;
                        }
                        System.out.println("------***-----\n");
                    }
                    if( totalFiles > 0 ){
                        while (true) {
                            try {
                                System.out.println("Do you want to download any file?");
                                System.out.print("Enter yes or no to continue : ");
                                String inp = input.nextLine();
                                if (inp.equalsIgnoreCase("yes")) {
                                    System.out.print("Please select the client(index no) whose file you want to download: ");
                                    int clientIndx = Integer.parseInt(input.nextLine());
                                    if( clientIndx < 1 || clientIndx > clients.size() )
                                        throw new NumberFormatException();
                                    System.out.print("Please select the file(index no) you want to download: ");
                                    int fileIndx = Integer.parseInt(input.nextLine());
                                    Vector<String> clientFiles = data.clientPublicFilesMap.get(clients.get(clientIndx-1));
                                    if( fileIndx < 1 || fileIndx > clientFiles.size() )
                                        throw new NumberFormatException();
                                    boolean success = downloadFile(clientFiles.get(fileIndx-1),clients.get(clientIndx-1),true);
                                    if(!success)
                                        System.out.println("file download failed");
                                    break;

                                }else if ( inp.equalsIgnoreCase("no") ){
                                    break;
                                }else
                                    throw new NumberFormatException();
                            }catch (NumberFormatException e){
                                System.out.println("Invalid Input");
                            }
                        }
                    }

                }
            }
            else if( reqID == 5 ) {
                if (receivedObject instanceof Data) {
                    Data data = (Data) receivedObject;

                    int i = 1;
                    if( data.responds.size() > 0 ){
                        System.out.println("\nResponds to your file requests : ");
                        for( FileRequest respond : data.responds ){
                            System.out.println(i++ + ".client : " + respond.responder +
                                    " responded to your following file request and uploaded the file." +
                                    "You can now download the file from his directory");
                            System.out.println("   FileName : " + respond.fileName);
                            System.out.println("   FileDesc : " + respond.fileDesc);
                            System.out.println("   Request ID : " + respond.reqID);
                            System.out.println("---------****----------");
                        }
                    }


                    System.out.println("\n\nYour File Requests : ");
                    System.out.println("Total Number of file Requests received : " + data.fileRequests.size());
                    i = 1;
                    for (FileRequest fileRequest : data.fileRequests) {
                        System.out.println(i++ + ".From: " + fileRequest.client);
                        System.out.println("   FileName : " + fileRequest.fileName);
                        System.out.println("   FileDesc : " + fileRequest.fileDesc);
                        System.out.println("   Request ID : " + fileRequest.reqID);
                        System.out.println("---------****----------");
                    }
                    int totalReq = data.fileRequests.size();
                    if( totalReq > 0  ){
                        while (true){
                            try{
                                System.out.println("Do you want to upload any file in response to the requests?");
                                System.out.print("Enter yes or no to continue : ");
                                String inp = input.nextLine();
                                if( inp.equalsIgnoreCase("yes") ){
                                    System.out.print("Please Select the request(index no) that you want to respond to : ");
                                    int choice = Integer.parseInt(input.nextLine());
                                    if( choice < 1 || choice > totalReq )
                                        throw new NumberFormatException();

                                    FileRequest fileRequest = data.fileRequests.get(choice-1);

                                    File file = new File("Client/ClientFiles/" + client.clientName + "/" + fileRequest.fileName);
                                    FileInputStream fileInputStream = null;

                                    try {
                                        fileInputStream = new FileInputStream(file);
                                        sendFileUploadRequest(file,fileRequest.fileName,true,fileInputStream, fileRequest.reqID);
                                    }catch ( FileNotFoundException e){
                                        System.out.println("No such file exists");
                                    }
                                    break;
                                }else if ( inp.equalsIgnoreCase("no") ){
                                    break;
                                }else
                                    throw new NumberFormatException();
                            }catch (NumberFormatException e){
                                System.out.println("Invalid Input");
                            }

                        }
                    }

                }
            }

        }catch (SocketTimeoutException ste){
            System.out.println(ste);
//          System.out.println("file download failed");
        }

        networkUtil.readFlush();
        networkUtil.writeFlush();

        networkUtil.socket.setSoTimeout(prevTimeout);

        client.state.setNormalState();
    }

    private boolean downloadFile(String fileName, String clientName,boolean isPublic) throws IOException, ClassNotFoundException {

        networkUtil.write(new DownloadReq(fileName,clientName,isPublic));
        Object receivedObject = networkUtil.read();

        if( receivedObject instanceof String ){
            String msg = (String) receivedObject;
            if( msg.equals("access granted") ){
                String filePath = "Client/ClientFiles/"+ client.clientName + "/"+fileName;
                FileOutputStream fileOutputStream = new FileOutputStream(filePath);
                DataInputStream dataInputStream = new DataInputStream(networkUtil.socket.getInputStream());
                byte[] buffer = new byte[client.chunkSize];
                int bytes = -1;
                try {
                    while ((bytes = dataInputStream.readInt()) != 0) {
                        dataInputStream.readFully(buffer, 0, bytes);
                        System.out.println("received bytes : " + bytes);
                        fileOutputStream.write(buffer, 0, bytes);
                    }

                    Object o = networkUtil.read();
                    if (o instanceof String) {
                        if (!((String) o).equals("SUCCESS")) {
                            fileOutputStream.close();
                            deleteFile(filePath);
                            return false;
                        }
                    } else {
                        fileOutputStream.close();
                        deleteFile(filePath);
                        return false;
                    }
                    System.out.println("file download successful");
                    fileOutputStream.close();
                    return true;
                }catch (SocketTimeoutException ste){
                    System.out.println(ste);
                    fileOutputStream.close();
                    deleteFile(filePath);
                    return false;
                }
            }else {
                System.out.println(msg);
                return false;
            }
        }else{
            System.out.println("received unknown object from server");
            return false;
        }

    }

    private void deleteFile(String filePath){

        File file = new File(filePath);

        // Check if the file exists
        if (file.exists()) {
            // Delete the file
            boolean deleted = file.delete();

            if (!deleted){
                System.out.println("Failed to delete the file.");
            }
        }

    }


    public void run() {
        try {
            input = new Scanner(System.in);
            while (true) {

                try{
                    System.out.println("\n\n-------$$$$--------\n");
                    System.out.println("1.look up list of clients");
                    System.out.println("2.look up your uploaded files");
                    System.out.println("3.look up public files of other clients");
                    System.out.println("4.Make a file request");
                    System.out.println("5.view all messages");
                    System.out.println("6.Upload a file");
                    System.out.println("7.Upload a file in response to request");
                    System.out.println("8.Exit");

                    int choice = Integer.parseInt(input.nextLine());

                    if(choice == 1 || choice == 2 || choice == 3 || choice == 5){
                        lookup(choice);
                    }else if(choice == 4){
                        makeFileRequest();
                    }else if(choice == 6){
                        System.out.print("Please Enter the fileName : ");
                        String fileName = input.nextLine();
                        System.out.println("Please Enter file Access : 1.public 2.private");
                        int fileAccess = Integer.parseInt(input.nextLine());
                        if(fileAccess != 1 && fileAccess != 2)
                            throw new NumberFormatException();

                        sendFileUploadRequest(fileName,fileAccess,-1L);
                    }else if(choice == 7){
                        System.out.print("Please Enter the fileName : ");
                        String fileName = input.nextLine();
                        System.out.println("Please Enter the valid request id( >= 0) :");
                        Long reqID = Long.parseLong(input.nextLine());
                        if( reqID < 0 )
                            throw new NumberFormatException();
                        sendFileUploadRequest(fileName,1,reqID);
                    }else if(choice == 8){
                        client.state.stopReadThreadReq();
                        break;
                    }
                    else
                        throw new NumberFormatException();


                }catch (NumberFormatException e){
                    System.out.println("Invalid Input");
                }

            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            try {
                networkUtil.closeConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void makeFileRequest() throws IOException, ClassNotFoundException {

        client.state.makeReadThreadWaitReq();

        networkUtil.readFlush();
        networkUtil.writeFlush();

        int prevTimeout = networkUtil.socket.getSoTimeout();
        networkUtil.socket.setSoTimeout(30*1000);

        System.out.println("Please Enter the file name");
        String fileName = input.nextLine();
        System.out.println("Please Enter a short description about the file");
        String fileDesc = input.nextLine();

        networkUtil.write(new FileRequest(fileName,fileDesc, client.clientName));

        networkUtil.readFlush();
        networkUtil.writeFlush();

        networkUtil.socket.setSoTimeout(prevTimeout);

        client.state.setNormalState();

    }
}



