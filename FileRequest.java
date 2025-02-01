import java.io.Serializable;

public class FileRequest implements Serializable {
    public String fileName;
    public String fileDesc;
    public Long reqID;
    public String client;
    public String responder = null;

    public FileRequest(String fileName,String fileDesc,String client){
        this.fileName = fileName;
        this.fileDesc = fileDesc;
        this.client = client;

    }
}
