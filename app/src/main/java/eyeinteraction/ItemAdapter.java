package eyeinteraction;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ItemAdapter extends BaseAdapter {
    private final Context mContext;
    private final Item[] items;

    public ItemAdapter(Context context, Item[] items){
        this.mContext = context;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.length;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final Item item = items[i];

        if(view == null){
            final LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            view = layoutInflater.inflate(R.layout.gridview_item, null);
            ImageView imgView = (ImageView)view.findViewById(R.id.picture);
            TextView txtView = (TextView)view.findViewById(R.id.text);
            final ViewHolder viewholder = new ViewHolder(imgView,txtView);
            view.setTag(viewholder);
        }

        final ViewHolder viewHolder = (ViewHolder)view.getTag();
        viewHolder.imgView.setImageResource(item.getImageResource());
        //change the color of an item to show that focus of attention is on this object
        viewHolder.imgView.setBackgroundColor(item.getIsSelected()? Color.RED: Color.GRAY);
//        //once selected, change the color of the object
//        viewHolder.imgView.setImageResource(item.getIsSelected()? R.drawable.imgselected: item.getImageResource());
        int itemID = item.getItemId() % EvaluationTwoiBlinkActivity.NumItemsPerTab == 0 ? EvaluationTwoiBlinkActivity.NumItemsPerTab: item.getItemId() % EvaluationTwoiBlinkActivity.NumItemsPerTab;
        viewHolder.txtView.setText("" + itemID);
        return view;
    }

    private class ViewHolder {
        private final TextView txtView;
        private final ImageView imgView;

        public ViewHolder(ImageView iv, TextView tv){
            this.imgView = iv;
            this.txtView = tv;
        }
    }
}



