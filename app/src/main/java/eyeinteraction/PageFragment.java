package eyeinteraction;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

public class PageFragment extends Fragment implements AdapterView.OnItemClickListener{
    private final String TAG = PageFragment.this.getClass().getSimpleName();
    AppCompatActivity mApp;
    private Item[] allItems;
    private Item[] items;
    private ItemAdapter mAdapter;
    private int fragmentId;
    private final int numItems = 4;
    private int selectedItemId = 0;

    public PageFragment(){
    }

    public void setParams(AppCompatActivity app, Item[] items, int fragmentId){
        this.fragmentId = fragmentId;
        this.mApp = app;
        this.allItems = items;
        this.items = new Item[numItems];
        for(int i = 0; i < numItems; i++){
            this.items[i] = items[this.fragmentId * this.numItems + i];
        }
        mAdapter = new ItemAdapter(mApp, this.items);
    }

    public ItemAdapter getAdapter() {
        return mAdapter;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        GridView mGridView = (GridView)rootView.findViewById(R.id.gridview);
        mGridView.setAdapter(mAdapter);
        mGridView.setOnItemClickListener(this);
        return rootView;
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {

        for(int i = 0; i < allItems.length; i++){
            Item item = allItems[i];
            if(i != this.fragmentId * numItems +  position){
                item.setIsSelected(false);
            }
            else {
                item.setIsSelected(true);
            }
        }
        ((EvaluationTwoTouchActivity)mApp).updateUIs();
        mAdapter.notifyDataSetChanged();
    }


    public void setItemSelected(int index){
        for(int i = 0; i < allItems.length; i++){
            Item item = allItems[i];
            if(i != index){
                item.setIsSelected(false);
            }
            else {
                item.setIsSelected(true);
            }
        }
        ((EvaluationTwoTouchActivity)mApp).updateUIs();
        mAdapter.notifyDataSetChanged();
    }
}