package LibraryServer;

/**
* LibraryServer/ServerFeaturesHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from ServerFeaturesInterface.idl
* Thursday, March 7, 2019 at 1:25:45 AM Eastern Standard Time
*/

public final class ServerFeaturesHolder implements org.omg.CORBA.portable.Streamable
{
  public LibraryServer.ServerFeatures value = null;

  public ServerFeaturesHolder ()
  {
  }

  public ServerFeaturesHolder (LibraryServer.ServerFeatures initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = LibraryServer.ServerFeaturesHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    LibraryServer.ServerFeaturesHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return LibraryServer.ServerFeaturesHelper.type ();
  }

}