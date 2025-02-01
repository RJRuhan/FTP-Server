import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class ReadThreadClient implements Runnable {
    private Thread thr;
    private NetworkUtil networkUtil;
    private Client client;

    public ReadThreadClient(NetworkUtil networkUtil,Client client) {
        this.networkUtil = networkUtil;
        this.thr = new Thread(this);
        this.client = client;
        thr.start();
    }

    private void handleThread() throws Exception {

        if (client.state.state == 0) {
            try {
//                System.out.println("reading");
                Object o = networkUtil.read();

                if (o instanceof String) {
                    String msg = (String)o;
                    System.out.println("Server : " + (String) o);
                }

            } catch (SocketTimeoutException e) {
                // socket timeout

            } catch (SocketException se) {
                System.out.println(se);
                throw se;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    networkUtil.closeConnection();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                throw e;
            }

        } else if (client.state.state == 1) {

//            System.out.println("ReadThread : wait request acknowledged");
            client.state.makeReadThreadWaitReqAck();

        } else if ( client.state.state == -1 ){
            client.state.stopReadThreadReqAck();
            throw new Exception();
        }
    }


    public void run() {

        while (true) {
            try {
                handleThread();
            } catch (Exception e) {
                break;
            }
        }

//        System.out.println("Read Thread Ended");


    }

}



