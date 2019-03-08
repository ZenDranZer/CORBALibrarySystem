import LibraryServer.ServerFeaturesPOA;
import org.omg.CORBA.ORB;
import java.io.*;
import java.net.*;
import java.util.*;


enum ServerDetails{

    CONCORDIA("CON",1301,0),
    MCGILL("MCG",1302,1),
    MONTREAL("MON",1303,2);

    private final String library;
    private final int port;
    private final int index;

    ServerDetails(String library,int port,int index){
        this.library = library;
        this.port = port;
        this.index = index;
    }

    public String getLibrary() {
        return library;
    }

    public int getPort() {
        return port;
    }

    public int getIndex() {
        return index;
    }
}


public class ServerObj extends ServerFeaturesPOA {

    protected final HashMap<String,User> user;
    protected final HashMap<String,Manager> manager;
    protected final HashMap<String,Item> item;
    protected final HashMap<User,HashMap<Item,Integer>> borrow;
    protected final HashMap<String, HashMap<String,Integer>> waitingQueue;      //itemID<userID,Integer>
    protected final HashMap<String,Integer> borrowedItemDays;
    protected String library;
    private Integer next_User_ID;
    private Integer next_Manager_ID;
    private File logFile;
    private PrintWriter logger;
    private final Object lock;
    private ORB orb;


    public ServerObj(String library){

        this.library = library;
        user = new HashMap<>();
        manager = new HashMap<>();
        item = new HashMap<>();
        borrow = new HashMap<>();
        waitingQueue = new HashMap<>();
        borrowedItemDays = new HashMap<>();
        next_User_ID = 1003;
        next_Manager_ID = 1002;
        lock = new Object();
        logFile = new File("/home/sarvesh/CORBALibrarySystem/src/Logs/log_" + library + ".log");
        try{
            if(!logFile.exists())
                logFile.createNewFile();
            logger = new PrintWriter(new BufferedWriter(new FileWriter(logFile)));
        }catch (IOException io){
            System.out.println("Error in creating log file.");
            io.printStackTrace();
        }
        writeToLogFile("Server " + library + " Started.");
        init();
    }

    public void setOrb(ORB orb) {
        this.orb = orb;
    }

    private void init(){
        String initManagerID = library + "M" + 1001;
        Manager initManager = new Manager(initManagerID);
        manager.put(initManagerID,initManager);
        writeToLogFile("Initial manager created.");
        String initUserID1001 = library + "U" + 1001;
        String initUserID1002 = library + "U" + 1002;
        User initUser1001 = new User(initUserID1001);
        User initUser1002 = new User(initUserID1002);
        user.put(initUserID1001,initUser1001);
        user.put(initUserID1002,initUser1002);
        writeToLogFile("Initial users created.");
        String initItemID1001 = library + 1001;
        String initItemID1002 = library + 1002;
        String initItemID1003 = library + 1003;
        Item initItem1001 = new Item(initItemID1001,"Distributed Systems",1);
        Item initItem1002 = new Item(initItemID1002,"Parallel Programming",6);
        Item initItem1003 = new Item(initItemID1003,"Algorithm Designs",7);
        item.put(initItemID1001,initItem1001);
        item.put(initItemID1002,initItem1002);
        item.put(initItemID1003,initItem1003);
        writeToLogFile("Initial items created.");
    }

    /**used to find the given item name in all library and return the itemID and availability.*/
    @Override
    public String findItem(String userID, String itemName) {
        if(userID.charAt(3) != 'U') {
            String message =
                    "Find Request : Server : " + library +
                            " User : " + userID +
                            " Item :" + itemName +
                            " Status : Unsuccessful " +
                            "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return message;
        }

        String reply = "";
        Iterator<Map.Entry<String, Item>> iterator;
        synchronized (lock) {
            iterator = item.entrySet().iterator();
        }
        while(iterator.hasNext()){
            Map.Entry<String,Item> pair = iterator.next();
            if(pair.getValue().getItemName().equals(itemName))
                reply = reply + "\n" + pair.getKey() + " " +pair.getValue().getItemCount();
        }
        reply += findAtOtherLibrary(itemName);
        reply = reply + "\nFind Request : Server : " + library +
                " User : " + userID +
                " Item :" + itemName +
                " Status : Successful ";
        writeToLogFile(reply);
        return reply;
    }

    /**It shows all the available books in that library to the manager.*/
    @Override
    public String listAvailability(String managerID) {
        if(managerID.charAt(3) != 'M') {
            String message =
                    "Item availability Request : Server : " + library +
                            " Manager : " + managerID +
                            "Status : Unsuccessful. " +
                            "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return message;
        }
        String reply =  "Item availability Request : Server : " + library +
                " Manager : " + managerID +
                "Status : Successful. " +
                "\nAvailability:\n";
        Iterator<Map.Entry<String,Item>> iterator;
        synchronized (lock) { iterator = item.entrySet().iterator(); }
        while(iterator.hasNext()){
            Map.Entry<String,Item> pair = iterator.next();
            reply += "\n" + pair.getValue().getItemName() + " " + pair.getValue().getItemCount() + "\n";
        }
        writeToLogFile(reply);
        return reply;
    }

    /**used to return the item borrowed by the user. If user has not borrowed any
     * book return unsuccessful note. Returned book will be automatically given
     * to those who are in the waiting queue.*/
    @Override
    public String returnItem(String userID, String itemID) {
        User currentUser;
        String message =
                "Return Request : Server : " + library +
                        " User : " + userID +
                        " Item :" + itemID +
                        "Status : ";
        if(userID.charAt(3) == 'U')
            currentUser = user.get(userID);
        else{
            message += "Unsuccessful. " +
                    "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return message;
        }

        if(!itemID.substring(0,3).equals(library)){
            message = returnToOtherLibrary(userID,itemID);
            return message;
        }
        if(borrow.containsKey(currentUser)){
            HashMap<Item,Integer> set = borrow.get(currentUser);
            Iterator<Map.Entry<Item,Integer>> value;
            synchronized (lock){ value = set.entrySet().iterator(); }
            if(!value.hasNext()){
                message +="Unsuccessful. " +
                        "\nNote : You have no borrowed item.";
                writeToLogFile(message);
                return message;
            }
            while(value.hasNext()) {
                Map.Entry<Item, Integer> pair = value.next();
                if(pair.getKey().getItemID().equals(itemID)){
                    synchronized (lock){
                        borrow.get(currentUser).remove(pair.getKey());
                        borrowedItemDays.remove(itemID);
                    }
                    increamentItemCount(itemID);
                    automaticAssignmentOfBooks(itemID);
                    message += " Successful";
                    writeToLogFile(message);
                    return message;
                }
            }
        }
        message += " Unsuccessful" +
                "\nNote: Item have never been borrowed";
        writeToLogFile(message);
        return message;
    }

    @Override
    public String borrowItem(String userID, String itemID, int numberOfDays) {
        User currentUser = user.get(userID);
        String reply =  "Borrow Request : Server : " + library +
                " User : " + userID +
                " Item :" + itemID;
        if(!item.containsKey(itemID)){
            reply = borrowFromOtherLibrary(userID,itemID,numberOfDays);
            writeToLogFile(reply);
            return reply;
        }

        Item requestedItem;
        requestedItem = item.get(itemID);
        if(requestedItem.getItemCount() == 0){
            return "queue";
        }else {
            HashMap<Item,Integer> entry;
            if (borrow.containsKey(currentUser)) {
                if (borrow.get(currentUser).containsKey(requestedItem)) {
                    reply += "\n Note : User have already borrowed." +
                            "\n Status : Unsuccessful";
                    return reply;
                } else {
                    entry = borrow.get(currentUser);
                    synchronized (lock){ borrow.remove(currentUser);}
                }
            } else {
                entry = new HashMap<>();
            }
            entry.put(requestedItem, numberOfDays);
            synchronized (lock) {
                borrow.put(currentUser, entry);
            }
            decrementItemCount(itemID);
            reply += "\n Status : Successful";
            borrowedItemDays.put(itemID,numberOfDays);
            writeToLogFile(reply);
            return reply;
        }
    }

    /**String addItem(String managerID, String itemID,String itemName, int quantity)*/
    @Override
    public String addItem(String managerID, String itemID, String itemName, int quantity) {
        String message =
                "Add Item Request : Server : " + library +
                        " Manager : " + managerID ;
        if(managerID.charAt(3) != 'M'){
            message += "Unsuccessful. " +
                    "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return message;
        }
        Item currentItem;
        if(authenticateItemID(itemID)){
            if(item.containsKey(itemID)){
                currentItem = item.get(itemID);
                currentItem.setItemCount(currentItem.getItemCount()+quantity);
                synchronized (lock){item.remove(itemID);}
            }else{
                currentItem = new Item(itemID,itemName,quantity);
            }
            synchronized (lock){item.put(itemID,currentItem);}
            message +=  " Item :" + itemID +
                    "Status : Successful.";
            automaticAssignmentOfBooks(itemID);
            writeToLogFile(message);
            return message;
        }
        message +=  " Item :" + itemID +
                "Status : Unsuccessful." +
                "\n Note : Invalid ItemID.";
        writeToLogFile(message);
        return message;
    }

    /**used by the manager to remove the item completely or decrease the quantity of it.*/
    @Override
    public String removeItem(String managerID, String itemID, int quantity) {
        String message =
                "Remove Request : Server : " + library +
                        " Manager : " + managerID +
                        " Item :" + itemID +
                        "Status : ";

        if(managerID.charAt(3) != 'M') {
            message += "Unsuccessful. " +
                    "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return message;
        }
        if(!item.containsKey(itemID)){
            message += "Unsuccessful. " +
                    "\nNote : The item do not exist in inventory.";
            writeToLogFile(message);
            return message;
        }
        Item currentItem = item.get(itemID);

        if(quantity < 0){
            item.remove(itemID);
            waitingQueue.remove(currentItem);
            for (Map.Entry<User, HashMap<Item, Integer>> pair : borrow.entrySet()) {
                if (pair.getValue().containsKey(currentItem)) {
                    borrow.get(pair.getKey()).remove(currentItem);
                }
            }
            message+= " Successful." +
                    "\nNote : All items have been removed.";
            writeToLogFile(message);
            return message;
        }else if(currentItem.getItemCount() < quantity){
            quantity = quantity - currentItem.getItemCount();
            synchronized (lock){item.remove(itemID);
                currentItem.setItemCount(0);
                item.put(itemID,currentItem);}
            message+= " Partially successful." +
                    "\nNote : Number of items in the inventory is less than desired quantity." +
                    "\n Balance quantity :" + quantity;
            writeToLogFile(message);
            return message;
        }else if(currentItem.getItemCount() > quantity){
            currentItem.setItemCount(currentItem.getItemCount() - quantity);
            synchronized (lock){
                item.remove(itemID);
                item.put(itemID,currentItem);
            }
            message += "Successful.";
            writeToLogFile(message);
            return message;
        }else{
            synchronized (lock){item.remove(itemID);}
            message += "Successful.";
            writeToLogFile(message);
            return message;
        }
    }

    /**allows the manager to create a user for that library. It returns new userID.*/
    @Override
    public String createUser(String managerID) {
        if(managerID.charAt(3) != 'M'){
            String message =
                    "New User Request : Server : " + library +
                            " Manager : " + managerID +
                            "Status : Unsuccessful. " +
                            "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return  message;
        }
        String userID = library + "U" + next_User_ID ;
        User currentUser = new User(userID);
        synchronized (lock) {user.put(userID,currentUser);
            next_User_ID += 1;}
        String message =
                "New User Request : Server : " + library +
                        " Manager : " + managerID +
                        " New User ID : "+ userID +
                        " Status : Successful.";
        writeToLogFile(message);
        return  message;
    }

    /**allows the manager to create a manager for that library. It returns new managerID.*/
    @Override
    public String createManager(String managerID) {
        if(managerID.charAt(3) != 'M'){
            String message =
                    "New User Request : Server : " + library +
                            " Manager : " + managerID +
                            "Status : Unsuccessful. " +
                            "\nNote : You are not allowed to use this feature.";
            writeToLogFile(message);
            return  message;
        }
        String newManagerID = library + "M" + next_Manager_ID ;
        Manager currentManager = new Manager(newManagerID);
        synchronized (lock) {manager.put(newManagerID,currentManager);
            next_Manager_ID += 1;}
        String message =
                "New User Request : Server : " + library +
                        " Manager : " + managerID +
                        " New Manager ID : "+ newManagerID +
                        " Status : Successful.";
        writeToLogFile(message);
        return  message;
    }

    /**It adds the given userID to the waiting list for given itemID with numberOfDays.*/
    @Override
    public String addToQueue(String userID, String itemID, int numberOfDays) {
        if(waitingQueue.containsKey(itemID)){
            waitingQueue.get(itemID).put(userID,numberOfDays);
        }else{
            HashMap<String,Integer> userList = new HashMap<>();
            userList.put(userID,numberOfDays);
            waitingQueue.put(itemID,userList);
        }
        return "Add to queue : User ID : "+ userID + " Item ID : " + itemID;
    }

    @Override
    public String validateClient(String clientID) {
        User currentUser;
        Manager currentManager;
        if(clientID == null){
            writeToLogFile("Validate clientID request : clientID : "+ clientID);
            return "false";
        }
        else if(clientID.charAt(3) == 'U'){
            synchronized(lock){ currentUser = user.get(clientID); }
            String message;
            if(currentUser == null){
                writeToLogFile("Validate clientID request : clientID : "+ clientID +" Status : Unsuccessful");
                return "false";
            }else{
                writeToLogFile("Validate clientID request : clientID : "+ clientID +" Status : Successful");
                message = "true";
                return message;
            }
        }
        else if(clientID.charAt(3) == 'M'){
            synchronized (this){
                currentManager = manager.get(clientID);
            }
            if(currentManager == null){
                writeToLogFile("Validate clientID request : clientID : "+ clientID +" Status : Unsuccessful");
                return "false";
            }else{
                writeToLogFile("Validate clientID request : clientID : "+ clientID +" Status : Successful");
                return  "true";
            }
        }
        writeToLogFile("Validate clientID request : clientID : "+ clientID + " Status : Unsuccessful");
        return "false";
    }

    @Override
    public String exchangeItem(String userID, String newItemID, String oldItemID) {
        User currentUser = user.get(userID);
        String reply =  "Exchange Request : Server : " + library +
                " User : " + userID +
                " old Item :" + oldItemID +
                " New Item : " + newItemID +
                " Status : ";
        String borrowReply,returnReply;
        /*First return the old item to particular library*/
        int numberOfDays = borrowedItemDays.get(oldItemID);
        if(borrow.containsKey(currentUser)){
            returnReply = returnItem(userID,oldItemID);
            if(returnReply.substring(returnReply.length()-10).equals("Successful")){
                borrowReply = borrowItem(userID,newItemID,numberOfDays);
                if(borrowReply.substring(borrowReply.length()-10).equals("Successful")){
                    reply += "Successful";
                }else{
                    reply += "Unsuccessful\n"+
                            "Note : Error in borrowing the new book.";
                }
            }else{
                reply += "Unsuccessful\n"+
                        "Note : Error in returning the old book.";
            }
        }else{
            reply += "Unsuccessful\n"+
                    "Note : you have not borrowed the mentioned old item.";
        }
        return reply;
    }

    @Override
    public void shutdown() {
        orb.shutdown(false);
    }

    /**It allows the user to borrow an item from other library. If not present
     * at all libraries then it will ask user whether to add the user into the queue or not.*/
    public String borrowFromOtherLibrary(String userID, String itemID, Integer numberOfDays){
        User currentUser;
        synchronized (lock) { currentUser = user.get(userID); }
        String reply =  "Borrow Request : Server : " + library +
                " User : " + userID +
                " Item :" + itemID +
                " Delegated Library : " + itemID.substring(0,3);
        try {
            DatagramSocket mySocket = new DatagramSocket();
            InetAddress host = InetAddress.getLocalHost();
            String result = "Unsuccessful";
            String request = library+":borrowFromOther:"+userID+":"+itemID+":"+numberOfDays;
            boolean isValidBorrow;
            ServerDetails serverDetails = null;
            switch (itemID.substring(0, 3)) {
                case "CON":
                    serverDetails = ServerDetails.CONCORDIA;
                    break;
                case "MCG":
                    serverDetails = ServerDetails.MCGILL;
                    break;
                case "MON":
                    serverDetails = ServerDetails.MONTREAL;
                    break;
            }
            isValidBorrow = currentUser.getOutsourced()[serverDetails.getIndex()];
            if(!isValidBorrow){
                DatagramPacket sendRequest = new DatagramPacket(request.getBytes(),request.length(),host,serverDetails.getPort());
                mySocket.send(sendRequest);
                byte[] receive = new byte[1024];
                DatagramPacket receivedReply = new DatagramPacket(receive,receive.length);
                mySocket.receive(receivedReply);
                result = new String(receivedReply.getData()).trim();
            }else{
                reply += "Note : You have already borrowed from this library." +
                        "Status : Unsuccessful";
            }
            if(result.equals("Successful")){
                reply += "Status : " + result;
                boolean[] isOutsourced;
                synchronized (lock) {isOutsourced = currentUser.getOutsourced();}
                isOutsourced[serverDetails.getIndex()] = true;
                synchronized (lock) {currentUser.setOutsourced(isOutsourced);}
                borrowedItemDays.put(itemID,numberOfDays);
                Item curruntItem;
                if(item.containsKey(itemID)){
                    curruntItem = item.get(itemID);
                    curruntItem.setItemCount(curruntItem.getItemCount()+1);
                    item.remove(itemID);
                }else{
                    curruntItem = new Item(itemID,serverDetails.getLibrary(),1);
                }
                item.put(itemID,curruntItem);
                if(borrow.containsKey(currentUser)){
                borrow.get(currentUser).put(curruntItem,numberOfDays);
                }else{
                    HashMap<Item,Integer> borrowed = new HashMap<>();
                    borrowed.put(curruntItem,numberOfDays);
                    borrow.put(currentUser,borrowed);
                }

            }else{
                reply = "queue";
            }
        }catch (SocketException e){
            writeToLogFile("Socket Exception");
            System.out.println("Socket Exception.");
            e.printStackTrace();
        }catch (UnknownHostException e){
            writeToLogFile("Unknown host Exception");
            System.out.println("Unknown host Exception.");
            e.printStackTrace();
        }catch (IOException e){
            writeToLogFile("IO Exception");
            System.out.println("IO Exception.");
            e.printStackTrace();
        }
        writeToLogFile(reply);
        return reply;
    }

    /**finds the given item name in other library and returns the itemID and availability.*/
    private String findAtOtherLibrary(String itemName){
        String reply = "\n";
        try{
            DatagramSocket mySocket = new DatagramSocket();
            InetAddress host = InetAddress.getLocalHost();
            int port1 = -1, port2 = -1;
            if (library.equals("CON")){
                port1 = 1302;
                port2 = 1303;
            }
            if (library.equals("MCG")){
                port1 = 1301;
                port2 = 1303;
            }
            if (library.equals("MON")){
                port1 = 1301;
                port2 = 1302;
            }
            String request = library+":findAtOther:"+itemName;
            DatagramPacket sendRequest = new DatagramPacket(request.getBytes(),request.length(),host,port1);
            mySocket.send(sendRequest);
            byte[] receive = new byte[1024];
            DatagramPacket receivedReply = new DatagramPacket(receive,receive.length);
            mySocket.receive(receivedReply);
            reply += new String(receivedReply.getData()).trim();
            reply += "\n";
            sendRequest = new DatagramPacket(request.getBytes(),request.length(),host,port2);
            mySocket.send(sendRequest);
            receive = new byte[1024];
            receivedReply = new DatagramPacket(receive,receive.length);
            mySocket.receive(receivedReply);
            reply += new String(receivedReply.getData()).trim();
        }catch (SocketException e){
            writeToLogFile("Socket Exception");
            System.out.println("Socket Exception.");
            e.printStackTrace();
        }catch (UnknownHostException e){
            writeToLogFile("Unknown host Exception");
            System.out.println("Unknown host Exception.");
            e.printStackTrace();
        }catch (IOException e){
            writeToLogFile("IO Exception");
            System.out.println("IO Exception.");
            e.printStackTrace();
        }
        writeToLogFile(reply);
        return reply;
    }

    /**it simply returns the given item to the library where it belongs to.*/
    private String returnToOtherLibrary(String userID, String itemID){
        User currentUser = user.get(userID);
        String reply ="";
        try{
            DatagramSocket mySocket = new DatagramSocket();
            InetAddress host = InetAddress.getLocalHost();
            ServerDetails serverDetails = null;
            if (itemID.substring(0,3).equals("CON")){
                serverDetails = ServerDetails.CONCORDIA;
            }
            if (itemID.substring(0,3).equals("MCG")){
                serverDetails = ServerDetails.MCGILL;
            }
            if (itemID.substring(0,3).equals("MON")){
                serverDetails = ServerDetails.MONTREAL;
            }
            String request = library+":returnToOther:"+userID+":"+itemID;
            DatagramPacket sendRequest = new DatagramPacket(request.getBytes(),request.length(),host,serverDetails.getPort());
            mySocket.send(sendRequest);
            byte[] receive = new byte[1024];
            DatagramPacket receivedReply = new DatagramPacket(receive,receive.length);
            mySocket.receive(receivedReply);
            String message = new String(receivedReply.getData()).trim();
            if(message.equals("Successful")){
                reply =  "Return Request : Server : " + library +
                        " User : " + userID +
                        " Item :" + itemID +
                        " Delegated Library : " + serverDetails.getLibrary() +
                        " Status : Successful";
                Item curruntItem;
                curruntItem = item.get(itemID);
                curruntItem.setItemCount(curruntItem.getItemCount()-1);
                item.remove(itemID);
                borrow.get(currentUser).remove(curruntItem);
                boolean[] isOutsourced;
                synchronized (lock) {isOutsourced = currentUser.getOutsourced();}
                isOutsourced[serverDetails.getIndex()] = false;
                synchronized (lock) {currentUser.setOutsourced(isOutsourced);}
                borrowedItemDays.remove(itemID);

            }else{
                reply =  "Return Request : Server : " + library +
                        " User : " + userID +
                        " Item :" + itemID +
                        " Delegated Library : " + serverDetails.getLibrary() +
                        " Status : Unsuccessful";
            }
        }catch (SocketException e){
            writeToLogFile("Socket Exception");
            System.out.println("Socket Exception.");
            e.printStackTrace();
        }catch (UnknownHostException e){
            writeToLogFile("Unknown host Exception");
            System.out.println("Unknown host Exception.");
            e.printStackTrace();
        }catch (IOException e){
            writeToLogFile("IO Exception");
            System.out.println("IO Exception.");
            e.printStackTrace();
        }
        writeToLogFile(reply);
        return reply;
    }

    /**it increases the item count by 1.*/
    protected synchronized void increamentItemCount(String itemID){
        Item currentItem = item.get(itemID);
        currentItem.setItemCount(currentItem.getItemCount()+1);
        item.remove(itemID);
        item.put(itemID,currentItem);
    }

    protected synchronized void decrementItemCount(String itemID){
        Item currentItem = item.get(itemID);
        currentItem.setItemCount(currentItem.getItemCount()-1);
        item.remove(itemID);
        item.put(itemID,currentItem);
    }

    /**to authenticate legal item id*/
    private boolean authenticateItemID(String id){
        return id.substring(0, 3).equals(library) && id.length() == 7;
    }

    /**to write the logs into log file.*/
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

    /**If the returned item is in the waiting queue list, it will
     * automatically assign the item to the first user and send
     * a message to the user.*/
    protected void automaticAssignmentOfBooks(String itemID) {
        if(waitingQueue.containsKey(itemID)){
            HashMap<String,Integer> userList = waitingQueue.get(itemID);
            Iterator<Map.Entry<String,Integer>> iterator = userList.entrySet().iterator();
            if(iterator.hasNext()){
                Map.Entry<String, Integer> pair = iterator.next();
                String message = borrowItem(pair.getKey(),itemID,pair.getValue());
                if(message.substring(message.length()-10).equals("Successful"))
                    waitingQueue.get(itemID).remove(pair.getKey());
                writeToLogFile(message);
            }
        }
    }
}
