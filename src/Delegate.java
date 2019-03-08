

/*
 * Library CON port = 1301
 * Library MCG port = 1302
 * Library MON port = 1303
 * */

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Delegate implements Runnable{

    private Integer port;
    private ServerObj library;

    public Delegate(Integer port, ServerObj library){
        this.port = port;
        this.library = library;
    }

    @Override
    public void run() {
        DatagramSocket mySocket = null;
        try {
            mySocket = new DatagramSocket(this.port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        while(true){
            try {
                byte collector[] = new byte[1024];
                DatagramPacket receiver = new DatagramPacket(collector,collector.length);
                mySocket.receive(receiver);
                Thread newThread = new Thread(new RequestHandler(library,mySocket,receiver));
                newThread.start();
            }catch(IOException e){
                System.out.println("Input/Output exception");
                e.printStackTrace();
            }
        }
    }
}

class RequestHandler implements Runnable {

    private ServerObj myServer = null;
    private DatagramSocket mySocket = null;
    private DatagramPacket receiver = null;
    private final Object lock;
    private File logFile;
    private PrintWriter logger;

    public RequestHandler(ServerObj myServer,DatagramSocket mySocket,DatagramPacket receiver){
        this.myServer = myServer;
        this.mySocket = mySocket;
        this.receiver = receiver;
        lock = new Object();
        logFile = new File("/home/sarvesh/CORBALibrarySystem/src/Logs/log_" + myServer.library + ".log");
        try{
            if(!logFile.exists())
                logFile.createNewFile();
            logger = new PrintWriter(new BufferedWriter(new FileWriter(logFile)));
        }catch (IOException io){
            System.out.println("Error in creating log file.");
            io.printStackTrace();
        }
        writeToLogFile(myServer.library + " Inter Server Started.");

    }
    /*format |    ServerName:RequestType:Argments     |
    ServerName = from which Server request came
    Request type =  Borrow from other lib.
                    Find at other lib.
                    Return to other lib.
     Arguments = [UserID]
                 [ItemID]
                 [ItemNama]
                 [NumberOfDays]
     */
    @Override
    public void run() {
        String data = new String(receiver.getData()).trim();
        String[] request = data.split(":");
        String reply = "";
        String userID,itemID,itemName;
        int numberOfDays;
        switch (request[1]){
            case "borrowFromOther" :
                if(request.length != 5){
                    reply = "Unsuccessful";
                    break;
                }
                userID = request[2];
                itemID = request[3];
                numberOfDays = Integer.parseInt(request[4]);
                if(!myServer.item.containsKey(itemID)){
                    reply = "Unsuccessful";
                    break;
                }
                Item requestedItem;
                synchronized (lock) {requestedItem = myServer.item.get(itemID);}
                if(requestedItem.getItemCount() == 0){
                    reply = "Unsuccessful";
                    break;
                }
                User currentUser = new User(userID);
                myServer.user.put(userID,currentUser);
                HashMap<Item,Integer> entry;
                myServer.decrementItemCount(itemID);
                if (myServer.borrow.containsKey(currentUser)) {
                    if (myServer.borrow.get(currentUser).containsKey(requestedItem)) {
                        reply = "Unsuccessful";
                        break;
                    } else {
                        synchronized (lock) {entry = myServer.borrow.get(currentUser);
                            myServer.borrow.remove(currentUser);}
                    }
                } else {
                    entry = new HashMap<>();
                }
                synchronized (lock) {entry.put(requestedItem, numberOfDays);
                    myServer.borrow.put(currentUser, entry);
                    myServer.borrowedItemDays.put(itemID,numberOfDays);
                }
                reply = "Successful";
                break;

            case "findAtOther" :
                if(request.length != 3){
                    reply = "Unsuccessful";
                    break;
                }
                itemName = request[2];
                Iterator<Map.Entry<String,Item>> iterator;
                synchronized (lock) {iterator = myServer.item.entrySet().iterator();}
                while(iterator.hasNext()){
                    Map.Entry<String,Item> pair = iterator.next();
                    if(pair.getValue().getItemName().equals(itemName))
                        reply = reply + "\n" + pair.getKey() + " " +pair.getValue().getItemCount();
                }
                break;

            case "returnToOther" :
                if(request.length != 4){
                    reply = "Unsuccessful";
                    break;
                }
                userID = request[2];
                itemID = request[3];
                synchronized (lock) {currentUser = myServer.user.get(userID);}
                Iterator<Map.Entry<Item,Integer>> value;
                synchronized (lock) {
                    if(myServer.borrow.containsKey(currentUser)){
                    value = myServer.borrow.get(currentUser).entrySet().iterator();
                    }else{
                        reply = "Unsuccessful";
                        break;
                    }
                }
                if(!value.hasNext()){
                    reply = "Unsuccessful";
                    break;
                }
                boolean status = false;
                while(value.hasNext()) {
                    Map.Entry<Item, Integer> pair = value.next();
                    if(pair.getKey().getItemID().equals(itemID)){
                        synchronized (lock) {
                            myServer.borrow.get(currentUser).remove(pair.getKey());
                            myServer.borrowedItemDays.remove(itemID);
                            myServer.user.remove(userID);
                            myServer.increamentItemCount(itemID);
                            myServer.automaticAssignmentOfBooks(itemID);
                        }
                        status = true;
                        break;
                    }
                }
                if (status) {
                    reply = "Successful";
                }else{
                    reply = "Unsuccessful";
                }
                break;

            case "isAvailable":
                if(request.length != 3){
                    reply = "Unsuccessful";
                    break;
                }
                itemID = request[2];
                if(myServer.item.containsKey(itemID)){
                    Item currentItem = myServer.item.get(itemID);
                    if(currentItem.getItemCount() >= 1)
                        reply = "true";
                    else
                        reply = "false";
                }else
                    reply = "false";
                break;
            default:
                reply = "Unsuccessful";
        }
        writeToLogFile(request[1] + " " + reply);
        DatagramPacket sender = new DatagramPacket(reply.getBytes(), reply.length(), receiver.getAddress(), receiver.getPort());
        try {
            mySocket.send(sender); // send the response DatagramPacket object to the requester.
        } catch (IOException e) {
            System.out.println("IO Exception");
            e.printStackTrace();
        }
    }

    synchronized private void writeToLogFile(String message) {
        try {
            if (logger == null)
                return;
            // print the time and the message to log file
            logger.println(Calendar.getInstance().getTime().toString() + " - " + message);
            logger.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}