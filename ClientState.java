public class ClientState {
    public volatile int state = 0;  // 0 -> normalState, 1 -> uploadReqState, 2-> uploadReqAckState
    public Client client;

    ClientState(Client client){
        this.client = client;
    }

    synchronized public void makeReadThreadWaitReq(){
        state = 1;

        while (state != 2) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized public void makeReadThreadWaitReqAck(){
        state = 2;

        notifyAll();
        while( state != 0 ){
            try {
                wait();
            } catch (InterruptedException e) {
                    e.printStackTrace();
            }
        }
    }

    synchronized public void setNormalState(){
        state = 0;
        notifyAll();
    }

    synchronized public void stopReadThreadReq(){
        state = -1;

        while (state != -2) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized public void stopReadThreadReqAck(){
        state = -2;
        notifyAll();
    }

}
