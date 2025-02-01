import java.io.Serializable;

public class UploadRequestAckMsg implements Serializable {
    public long fileID;
    public int chunkSize;
    public boolean confirm;
    public String msg;

    UploadRequestAckMsg(long fileID, int chunkSize,boolean confirm){
        this.fileID = fileID;
        this.chunkSize = chunkSize;
        this.confirm = confirm;
    }

}
