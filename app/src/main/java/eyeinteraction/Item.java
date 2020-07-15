package eyeinteraction;

public class Item {

    private int imageResource;
    private boolean isSelected = false;
    private int itemId;

    public Item(int itemId , int imageResource){
        this.itemId = itemId + 1;  //make sure the ID starts at 1 instead of 0
        this.imageResource = imageResource;
    }

    public int getItemId(){
        return this.itemId;
    }

    public int getImageResource(){
        return imageResource;
    }

    public void setImageResource(int imageResource){
        this.imageResource = imageResource;
    }

    public boolean getIsSelected(){
        return isSelected;
    }

    public void setIsSelected(boolean isSelected){
        this.isSelected = isSelected;
    }

    public void toggleSelection(){
        isSelected = !isSelected;
    }

}
