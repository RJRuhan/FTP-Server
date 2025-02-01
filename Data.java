import java.io.Serializable;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

public class Data implements Serializable {
    public Set<String> onlineClients;
    public Set<String> offlineClients;

    public Vector<String> publicFiles;
    public Vector<String> privateFiles;

    public HashMap<String,Vector<String>> clientPublicFilesMap;
    public Vector<FileRequest> fileRequests;
    public Vector<FileRequest> responds;
}
