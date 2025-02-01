import java.io.Serializable;

public class UploadFileRequest implements Serializable {

    public long fileSize;
    public String fileName;
    public String clientName;
    public boolean isPublic;
    public Long reqID ;


    UploadFileRequest(Long fileSize, String fileName,boolean isPublic,String clientName,Long reqID){
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.isPublic = isPublic;
        this.clientName = clientName;
        this.reqID = reqID;
    }


}
