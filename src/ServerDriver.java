import LibraryServer.ServerFeatures;
import LibraryServer.ServerFeaturesHelper;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Object;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;


public class ServerDriver {
    public static void main(String[] args) {
        try{
            // create and initialize the ORB //// get reference to rootpoa &amp; activate the POAManager
            ORB orb = ORB.init(args, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // create servant and register it with the ORB
            ServerObj concordia = new ServerObj("CON");
            ServerObj mcgill = new ServerObj("MCG");
            ServerObj montreal = new ServerObj("MON");
            concordia.setOrb(orb);
            mcgill.setOrb(orb);
            montreal.setOrb(orb);

            Thread concordiaDelegate = new Thread(new Delegate(1301,concordia));
            concordiaDelegate.start();
            Thread mcgillDelegate = new Thread(new Delegate(1302,mcgill));
            mcgillDelegate.start();
            Thread montrealDelegate = new Thread(new Delegate(1303,montreal));
            montrealDelegate.start();

            // get object reference from the servant
            Object concordiaRef = rootpoa.servant_to_reference(concordia);
            Object mcgillRef = rootpoa.servant_to_reference(mcgill);
            Object montrealRef = rootpoa.servant_to_reference(montreal);

            ServerFeatures concordiaHref = ServerFeaturesHelper.narrow(concordiaRef);
            ServerFeatures mcgilHref = ServerFeaturesHelper.narrow(mcgillRef);
            ServerFeatures montrealHref = ServerFeaturesHelper.narrow(montrealRef);


            Object objRef =  orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            NameComponent[] concordiaPath = ncRef.to_name("CON");
            ncRef.rebind(concordiaPath, concordiaHref);

            NameComponent[] mcgillPath = ncRef.to_name("MCG");
            ncRef.rebind(mcgillPath, mcgilHref);

            NameComponent[] montrealPath = ncRef.to_name("MON");
            ncRef.rebind(montrealPath, montrealHref);

            System.out.println("All library Servers are ready and waiting ...");

            while(true){orb.run();}
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
