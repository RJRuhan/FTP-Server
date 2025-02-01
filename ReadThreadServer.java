import java.io.*;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

public class ReadThreadServer implements Runnable {

    private final NetworkUtil networkUtil;
    private final String clientName;
    private final String client_Thread;
    private final Server server;
    private UploadFileRequest currFileMetaData = null;
    private int currFileChunkSize = -1;
    private String currFilePath;
    private Long currFileId ;


    public ReadThreadServer(NetworkUtil networkUtil,String clientName,Server server) {
        this.networkUtil = networkUtil;
        Thread thr = new Thread(this);
        this.clientName = clientName;
        this.client_Thread = clientName + "_Read Thread : ";
        this.server = server;
        thr.start();
    }

    private void sendFileToClient(DownloadReq downloadReq) throws IOException {

        System.out.println(client_Thread + "Download Request received");
        System.out.println("fileName : " + downloadReq.fileName + "  Owner : " + downloadReq.ownerOfFile);

        ClientData clientFilesList = server.clientDataMap.get(downloadReq.ownerOfFile);
        if( clientFilesList == null ){
            networkUtil.write("No such client exists");
            return;
        }

        if( !downloadReq.isPublic && !downloadReq.ownerOfFile.equals(clientName)){
            networkUtil.write("Access Denied");
            return;
        }

        if( downloadReq.isPublic && !clientFilesList.publicFiles.contains(downloadReq.fileName) ){
            networkUtil.write("No such file exists");
            return;
        }else if( !downloadReq.isPublic && !clientFilesList.privateFiles.contains(downloadReq.fileName) ){
            networkUtil.write("No such file exists");
            return;
        }

        String filePath = "Server/clientFiles/" + downloadReq.ownerOfFile + "/" +
                (downloadReq.isPublic?"public":"private")+"/"+downloadReq.fileName;

        File file = new File(filePath);
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
        }catch ( FileNotFoundException e){
            networkUtil.write("No such file exists");
            return;
        }

        networkUtil.write("access granted");

        DataOutputStream dataOutputStream = new DataOutputStream(networkUtil.socket.getOutputStream());
        byte[] buffer = new byte[server.maxChunkSize];
        int bytes = -1;

        while ( (bytes=fileInputStream.read(buffer,0,server.maxChunkSize)) != -1 ){
            dataOutputStream.writeInt(bytes);
            dataOutputStream.write(buffer,0,bytes);
        }

        dataOutputStream.writeInt(0);
//        try {
//            Thread.sleep(40000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        networkUtil.write("SUCCESS");
        System.out.println(client_Thread+"file sent");
    }


    public void run() {
        try {
            while (true) {
                Object receivedObject = networkUtil.read();
                if(receivedObject instanceof UploadFileRequest){
                    UploadFileRequest fileMetaData = (UploadFileRequest) receivedObject;
                    System.out.println( client_Thread + "upload request received ");
                    System.out.print("fileName : " + fileMetaData.fileName + "  fileSize : " + fileMetaData.fileSize + " Bytes\n");
                    sendConfirmationMsg(fileMetaData);
                }else if ( receivedObject instanceof LookupRequest ){
                    LookupRequest lookupRequest = (LookupRequest) receivedObject;
                    if( lookupRequest.reqID == 1 ){
                        Data data = new Data();
                        data.onlineClients = new HashSet<>(server.clientNetworkMap.keySet());
                        data.onlineClients.remove(clientName);
                        data.offlineClients = new HashSet<>(server.clientDataMap.keySet());
                        data.offlineClients.removeAll(server.clientNetworkMap.keySet());
                        data.offlineClients.remove(clientName);
                        networkUtil.write(data);
                    }else if( lookupRequest.reqID == 2 ){
                        Data data = new Data();
                        data.publicFiles = new Vector<>(server.clientDataMap.get(clientName).publicFiles);
                        data.privateFiles = new Vector<>(server.clientDataMap.get(clientName).privateFiles);

                        networkUtil.write(data);
                    }else if( lookupRequest.reqID == 3 ){
                        Data data = new Data();
                        data.clientPublicFilesMap = new HashMap<>();
                        for( String client : server.clientDataMap.keySet() ){
                            data.clientPublicFilesMap.put(client,new Vector<String>(server.clientDataMap.get(client).publicFiles));
                        }

                        networkUtil.write(data);
                    }else if( lookupRequest.reqID == 5 ){
                        Data data = new Data();
                        ClientData clientData = server.clientDataMap.get(clientName);
                        data.fileRequests = new Vector<>(clientData.fileRequestsInbox);
                        data.responds = new Vector<>(clientData.responsesInbox);
                        networkUtil.write(data);
                        synchronized (server.clientDataMap){
                            clientData.fileRequestsInbox.clear();
                            clientData.responsesInbox.clear();
                            server.writeToFile("Server/database.dat");
                        }
                    }
                }else if( receivedObject instanceof DownloadReq ){
                    sendFileToClient((DownloadReq) receivedObject);
                }else if( receivedObject instanceof FileRequest ){
                    FileRequest fileRequest = (FileRequest)receivedObject;
                    System.out.println("File Request Received");
                    System.out.println("fileName :" + fileRequest.fileName);
                    System.out.println("fileDesc :" + fileRequest.fileDesc);

                    synchronized (server.clientDataMap){
                        fileRequest.reqID = server.reqIdGenerator.generateId();
                        server.fileRequests.put(fileRequest.reqID,fileRequest);
                        for( String client : server.clientDataMap.keySet() ){
                            if(client.equals(clientName))
                                continue;
                            server.clientDataMap.get(client).fileRequestsInbox.add(fileRequest);
                        }
                        server.writeToFile("Server/database.dat");
                    }


                }

            }
        }catch (EOFException | SocketException e){
            System.out.println(client_Thread + "disconnected");
            server.clientNetworkMap.remove(clientName);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            try {
                networkUtil.closeConnection();
            } catch (IOException e) {
//              e.printStackTrace();
                System.out.println(e);
            }
        }
    }

    private void sendConfirmationMsg(UploadFileRequest fileMetaData) throws InterruptedException, IOException {

        int chunkSize = -1;

        if( fileMetaData.reqID != -1 ){
            FileRequest fileRequest = server.fileRequests.get(fileMetaData.reqID);
            if(fileRequest == null){
                UploadRequestAckMsg ackMsg = new UploadRequestAckMsg(-1,-1,false);
                ackMsg.msg = "File upload request rejected due to invalid request id...";
                networkUtil.write(ackMsg);
                System.out.println(client_Thread+"File upload request rejected due to invalid request id...");
                return;
            }
            if( !fileRequest.fileName.equals(fileMetaData.fileName) ){
                UploadRequestAckMsg ackMsg = new UploadRequestAckMsg(-1,-1,false);
                ackMsg.msg = "File upload request rejected because file name doesnt match with requested file name...";
                networkUtil.write(ackMsg);
                System.out.println(client_Thread+"File upload request rejected because file name doesnt match with requested file name...");
                return;
            }
        }

        synchronized (server.buffer){
            if( server.buffer.currBufferSize + fileMetaData.fileSize > server.maxBufferSize ){
                UploadRequestAckMsg ackMsg = new UploadRequestAckMsg(-1,-1,false);
                ackMsg.msg = "File upload request rejected due to buffer being full...";
                networkUtil.write(ackMsg);
                System.out.println(client_Thread+"File upload request rejected due to buffer being full...");
                return;
            }else{
                chunkSize = (int) (Math.random()*(server.maxChunkSize-server.minChunkSize+1)+server.minChunkSize);
                if( chunkSize > fileMetaData.fileSize ){
                    chunkSize = (int) (fileMetaData.fileSize + 100);
                }
                server.buffer.currBufferSize += chunkSize;
//                System.out.println(client_Thread + "buffer capacity : " + server.buffer.currBufferSize);
//                Thread.sleep(15000);
            }
        }

        currFileMetaData = fileMetaData;
        currFileChunkSize = chunkSize;
        String fileAccess = fileMetaData.isPublic ?"public":"private";
        currFilePath = "Server/clientFiles/"+clientName+ "/"+ fileAccess+"/" + currFileMetaData.fileName;

        synchronized (server.filesReceiving){
            currFileId = server.fileIdGenerator.generateId();
            server.filesReceiving.put(currFileId,currFileMetaData);
//            System.out.println(client_Thread+"here"+fileID);
//            Thread.sleep(15000);
        }
        UploadRequestAckMsg ackMsg = new UploadRequestAckMsg(currFileId,chunkSize,true);
        networkUtil.write(ackMsg);

        try{
            receiveFile();
        } catch (Exception e) {
            System.out.println(client_Thread + "file upload failed...");
            System.out.println(e.getMessage());
            deleteFile();
            if( e.getMessage().equals("Timeout Error:#1331!") ){

            }else{
                try {
                    networkUtil.write("error");
                }catch (Exception ee){
                    System.out.println(ee);
                }
            }
        }finally {
            synchronized (server.buffer){
                server.buffer.currBufferSize -= chunkSize;
//                System.out.println(server.buffer.currBufferSize);
            }
            synchronized (server.filesReceiving){
                server.filesReceiving.remove(currFileId);
            }

        }

    }

    private void deleteFile(){

        File file = new File(currFilePath);

        // Check if the file exists
        if (file.exists()) {
            // Delete the file
            boolean deleted = file.delete();

            if (deleted) {
                System.out.println("File deleted successfully.");
            } else {
                System.out.println("Failed to delete the file.");
            }
        } else {
            System.out.println("File does not exist.");
        }

    }



    private void receiveFile() throws Exception {

        int bytes = 0;
        FileOutputStream fileOutputStream = new FileOutputStream(currFilePath);
        byte[] buffer = new byte[currFileChunkSize];
        int chunkIndex = 1;
        long remainingSize = currFileMetaData.fileSize;
        int prevSoTimeout = networkUtil.socket.getSoTimeout();
        networkUtil.socket.setSoTimeout(30*1000);
        DataInputStream dataInputStream = new DataInputStream(networkUtil.socket.getInputStream());
        try {
            while ( remainingSize > 0 ) {
                int code = dataInputStream.readInt();
                if( code == -1 ){
                    System.out.println(client_Thread + "timeout message received");
                    throw new Exception("Timeout Error:#1331!");
                }

                if( ( bytes=dataInputStream.read(buffer,0, (int) Math.min(remainingSize,currFileChunkSize)) ) == -1)
                    break;
                System.out.println(client_Thread  + "received chunk No : " + chunkIndex);
//                Thread.sleep(40000);
                networkUtil.write("chunk received");
                fileOutputStream.write(buffer,0,bytes);
                remainingSize -= bytes;
                chunkIndex++;
//                Thread.sleep(500);
            }
            System.out.println("received chunks total size : " + (currFileMetaData.fileSize - remainingSize));
            Object o = networkUtil.read();
            if ( o instanceof String ){
                String completionMsg = (String) o;
                if( completionMsg.equals("success") ){
                    if( remainingSize == 0 ){
                        System.out.println("file upload successful...");
                        networkUtil.write("success");

                        synchronized (server.clientDataMap){

                            if( currFileMetaData.isPublic)
                                server.clientDataMap.get(clientName).publicFiles.add(currFileMetaData.fileName);
                            else
                                server.clientDataMap.get(clientName).privateFiles.add(currFileMetaData.fileName);
                            if( currFileMetaData.reqID != -1 ){
                                FileRequest fileRequest = server.fileRequests.get(currFileMetaData.reqID);
                                FileRequest fileRequest1 = new FileRequest(fileRequest.fileName,fileRequest.fileDesc,fileRequest.client);
                                fileRequest1.responder = clientName;
                                fileRequest1.reqID = currFileMetaData.reqID;
                                server.clientDataMap.get(fileRequest.client).responsesInbox.add(fileRequest1);
                            }

                            server.writeToFile("Server/database.dat");
//                            Thread.sleep(15000);
                        }

                    }else
                        throw new Exception("received file size does not match with meta data");
                }else
                    throw new Exception("Completion msg received from client != success");
            }else
                throw new Exception("Didnt receive completion msg from client");
        }catch (SocketTimeoutException se){
            throw new Exception("socket timeout : Didnt receive completion msg from client");
        } finally {
            networkUtil.socket.setSoTimeout(prevSoTimeout);
            fileOutputStream.close();
        }
    }
}



