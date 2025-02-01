import java.io.Serializable;

public class DownloadReq implements Serializable {

    public String fileName;
    public String ownerOfFile;
    public boolean isPublic;

    public DownloadReq(String fileName,String ownerOfFile,boolean isPublic){
        this.fileName = fileName;
        this.ownerOfFile = ownerOfFile;
        this.isPublic = isPublic;
    }

}
