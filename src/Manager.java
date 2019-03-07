public class Manager {

    private String managerID;
    private String library;
    private String index;

    public Manager(String managerID){
        this.managerID = managerID;
        this.library = managerID.substring(0,3);
        this.index = managerID.substring(4);
    }

    public String getManagerID() {
        return managerID;
    }

    public void setManagerID(String managerID) {
        this.managerID = managerID;
    }

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

}
