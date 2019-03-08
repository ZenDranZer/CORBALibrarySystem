import LibraryServer.ServerFeatures;
import LibraryServer.ServerFeaturesHelper;
import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import java.io.*;
import java.rmi.RemoteException;
import java.util.Calendar;

public class Client implements Runnable {

    private String clientID;
    private String library;
    private String type;
    private String index;
    private BufferedReader sc;
    private File logFile;
    private PrintWriter logger;
    private String[] args;
    ServerFeatures client;

    public Client(String clientID, String[] args){
        this.clientID = clientID;
        this.library = clientID.substring(0,3);
        this.type = String.valueOf(clientID.charAt(3));
        this.index = clientID.substring(4);
        this.args = args;
        try {
            ORB orb = ORB.init(args, null);
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            client = ServerFeaturesHelper.narrow(ncRef.resolve_str(library));
        }catch (InvalidName | CannotProceed | org.omg.CosNaming.NamingContextPackage.InvalidName | NotFound invalidName) {
            writeToLogFile("Invalid name.");
            System.out.println("Invalid name.");
            invalidName.printStackTrace();
        }
        sc = new BufferedReader(new InputStreamReader(System.in));
        logFile = new File("/home/sarvesh/CORBALibrarySystem/src/Logs/log_" + library + "_"+ clientID+ ".log");
        try{
            if(!logFile.exists())
                logFile.createNewFile();
            logger = new PrintWriter(new BufferedWriter(new FileWriter(logFile)));
        }catch (IOException io){
            System.out.println("Error in creating log file.");
            io.printStackTrace();
        }
        writeToLogFile("User " + clientID + " Started.");
    }

    private void printUserOptions(){
        System.out.println("Features :");
        System.out.println("1) Borrow an item.");
        System.out.println("2) Find an Item.");
        System.out.println("3) Return an Item.");
        System.out.println("4) Exchange Items.");
        System.out.println("Press 'N' or 'n' to exit.");
    }

    private void printManagerOptions(){
        System.out.println("Features :");
        System.out.println("1) Add an item.");
        System.out.println("2) Remove an Item.");
        System.out.println("3) List Item Availability.");
        System.out.println("4) Create User.");
        System.out.println("5) Create Manager.");
        System.out.println("6) Shutdown.");
        System.out.println("Press 'N' or 'n' to exit.");
    }

    @Override
    public void run() {
        if(type.equals("U"))
            userInterface();
        else
            managerInterface();
    }

    public void userInterface(){
        char op = 'Y';
        try {
            String response = client.validateClient(clientID);
            if(response.equals("false")) {
                System.out.println("Provided ID is wrong!! please invoke the client again.");
                writeToLogFile("User id: " + clientID + " Provided ID is wrong!! please invoke the client again.");
                System.exit(0);
            }
            System.out.println("Hello " + clientID);
            while (op == 'Y' || op == 'y'){
                printUserOptions();
                op = sc.readLine().charAt(0);
                switch (op){
                    case '1':
                        System.out.println("Borrow Item Section :");
                        System.out.println("Enter Item ID: ");
                        String itemID = sc.readLine();
                        System.out.println("Enter for how many days you want to borrow ?");
                        Integer numberOfDays = new Integer(sc.readLine());
                        String reply = client.borrowItem(clientID,itemID,numberOfDays);
                        System.out.println("Reply from server : " + reply);
                        writeToLogFile(reply);
                        if(reply.equals("queue")){
                            System.out.println("Item is not available at all library, do wanna put yourself in a queue (Y/N) ? ");
                            String ch = sc.readLine();
                            if (ch.charAt(0) == 'Y' || ch.charAt(0) == 'y'){
                                reply = client.addToQueue(clientID,itemID,numberOfDays);
                                writeToLogFile(reply);
                                System.out.println(reply);
                            }
                        }
                        op = 'Y';
                        break;
                    case '2':
                        System.out.println("Find Item Section :");
                        System.out.println("Enter Item Name :");
                        itemID = sc.readLine();
                        reply = client.findItem(clientID,itemID);
                        writeToLogFile(reply);
                        System.out.println("Reply from server : " + reply);
                        op = 'Y';
                        break;
                    case '3':
                        System.out.println("Return Item Section :");
                        System.out.println("Enter Item ID :");
                        itemID = sc.readLine();
                        reply = client.returnItem(clientID,itemID);
                        writeToLogFile(reply);
                        System.out.println("Reply from server : " + reply);
                        op = 'Y';
                        break;
                    case '4':
                        System.out.println("Exchange Item Section :");
                        System.out.println("Enter new Item ID :");
                        String newItemID = sc.readLine();
                        System.out.println("Enter old Item ID :");
                        String oldItemID = sc.readLine();
                        reply = client.exchangeItem(clientID,newItemID,oldItemID);
                        writeToLogFile(reply);
                        System.out.println("Reply from server : " + reply);
                        op = 'Y';
                        break;
                    case 'N':
                        writeToLogFile("User Quit : UserID : " + clientID);
                        break;
                    case 'n':
                        writeToLogFile("User Quit : UserID : " + clientID);
                        break;
                    default:
                        System.out.println("Wrong Selection!");
                        op = 'Y';
                        break;
                }
            }
            System.out.println("Bye " + clientID);
            System.exit(0);
        } catch(RemoteException e){
            writeToLogFile("Remote Exception");
            System.out.println("Remote Exception.");
            e.printStackTrace();
        } catch (IOException e){
            writeToLogFile("IO Exception");
            System.out.println("IO Exception.");
            e.printStackTrace();
        }
    }

    public void managerInterface(){
        char op = 'Y';
        try {
            if(client.validateClient(clientID).equals("false")) {
                System.out.println("Provided ID is wrong!! please invoke the client again.");
                writeToLogFile("UserID : " + clientID +" Provided ID is wrong!! please invoke the client again. ");
                System.exit(0);
            }
            System.out.println("Hello " + clientID);
            while (op == 'Y' || op == 'y'){
                printManagerOptions();
                op = sc.readLine().charAt(0);
                switch (op){
                    case '1':
                        System.out.println("Enter Item ID: ");
                        String itemID = sc.readLine();
                        System.out.println("Enter Item Name: ");
                        String itemName = sc.readLine();
                        System.out.println("Enter Item quantity: ");
                        Integer quantity = new Integer(sc.readLine());
                        String reply = client.addItem(clientID,itemID,itemName,quantity);
                        writeToLogFile(reply);
                        System.out.println("Reply from Server : " + reply);
                        op = 'Y';
                        break;
                    case '2':
                        System.out.println("Enter Item ID: ");
                        itemID = sc.readLine();
                        System.out.println("Enter Item quantity: ");
                        quantity = new Integer(sc.readLine());
                        reply = client.removeItem(clientID,itemID,quantity);
                        writeToLogFile(reply);
                        System.out.println("Reply from Server : " + reply);
                        op = 'Y';
                        break;
                    case '3':
                        reply = client.listAvailability(clientID);
                        writeToLogFile(reply);
                        System.out.println("Reply from Server : \n" + reply);
                        op = 'Y';
                        break;
                    case '4':
                        reply = client.createUser(clientID);
                        writeToLogFile(reply);
                        System.out.println("Reply from Server : \n" + reply);
                        op = 'Y';
                        break;
                    case '5':
                        reply = client.createManager(clientID);
                        writeToLogFile(reply);
                        System.out.println("Reply from Server : \n" + reply);
                        op = 'Y';
                        break;
                    case '6':
                        client.shutdown();
                        break;
                    case 'N':
                        writeToLogFile("Manager Quit : UserID : " + clientID);
                        break;
                    case 'n':
                        writeToLogFile("Manager Quit : UserID : " + clientID);
                        break;
                    default:
                        System.out.println("Wrong Selection!");
                        op = 'Y';
                        break;
                }
            }
            System.out.println("Bye " + clientID);
            System.exit(0);
        } catch(IOException e){
            System.out.println("IO Exception.");
            writeToLogFile("IO Exception");
            e.printStackTrace();
        }
    }

    synchronized private void writeToLogFile(String message) {
        try {
            if (logger == null)
                return;
            logger.println(Calendar.getInstance().getTime().toString() + " - " + message);
            logger.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
