import java.util.Scanner;

public class Item {
    private String itemID;
    private String library;
    private String index;
    private String itemName;
    private Integer itemCount;

    public Item(String itemID){
        this.itemID = itemID;
        this.library = itemID.substring(0,3);
        this.index = itemID.substring(3);
        getDetails();
    }

    public Item(String itemID,String itemName, Integer itemCount){
        this.itemID = itemID;
        this.itemName = itemName;
        this.itemCount = itemCount;
    }

    private void getDetails(){
        Scanner sc = new Scanner(System.in);
        System.out.println("Item ID: " + itemID);
        System.out.println("Enter Item name: " );
        itemName = sc.nextLine();
        System.out.println("Enter Item quantity: ");
        itemCount = sc.nextInt();
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

    public String getItemID() {
        return itemID;
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
