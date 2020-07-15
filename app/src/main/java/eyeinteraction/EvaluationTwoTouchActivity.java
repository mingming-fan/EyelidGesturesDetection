package eyeinteraction;


import android.graphics.Color;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class EvaluationTwoTouchActivity extends AppCompatActivity implements GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener{
    private final String TAG = EvaluationTwoTouchActivity.this.getClass().getSimpleName();

    private final String[] AppNames = {"APP 1", "APP 2", "APP 3"};
    private int AppId = 0; //current simulated app id
    private final int[] resourceIds = {R.drawable.item1, R.drawable.item2, R.drawable.item3};
    private final int[] appColors = {Color.GREEN, Color.YELLOW, Color.BLUE};
    private final int NumItems = 12;  //12 items in total
    private Item[] items = new Item[NumItems];
    private final String[] Page_titles = new String[]{
            "Item 1-4",
            "Item 5-8",
            "Item 9-12"
    }; //three tabs' titles

    private ClickableViewPager mViewPager;
    private MyPagerAdapter mAdapter;
    private TabLayout mTablayout;
    private TextView mTitle;
    private GestureDetectorCompat mDetector;

    String participantCode;
//    String glassCode;
    int numOfTargets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_two_touch);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");
//        glassCode = b.getString("glassCode");
        String repeats = b.getString("numTargets");
        numOfTargets = Integer.parseInt(repeats);

//        String filename = participantCode + "_" + glassCode + "_2_touch_" + System.currentTimeMillis() + ".csv";
        String filename = participantCode + "_2_touch_" + System.currentTimeMillis() + ".csv";

        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mTitle = (TextView) mToolbar.findViewById(R.id.toolbar_title);
        mTitle.setTextColor(appColors[AppId]);
        //set Activity title to empty to hide it
        getSupportActionBar().setTitle("");

        mViewPager = (ClickableViewPager) findViewById(R.id.viewpager);
        mAdapter = new MyPagerAdapter(getSupportFragmentManager());

        for(int j = 0; j < NumItems; j++){
            Item mItem = new Item(j, resourceIds[AppId]);
            items[j] = mItem;
        }

        for(int j = 0; j < Page_titles.length; j++){
            PageFragment fragment = new PageFragment();
            fragment.setParams(this, items, j);
            mAdapter.addFragment(fragment, Page_titles[j]);
        }
        mViewPager.setAdapter(mAdapter);

        mTablayout = (TabLayout) findViewById(R.id.tab_layout);
        mTablayout.setupWithViewPager(mViewPager);

//        mViewPager.setOnClickListener(this);

        mDetector = new GestureDetectorCompat(this,this);
        // Set the gesture detector as the double tap
        // listener.
        mDetector.setOnDoubleTapListener(this);

        mViewPager.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.i(TAG, "onTOuch in ViewPager");
                return mDetector.onTouchEvent(motionEvent);
            }});
    }

//    @Override
//    public void onClick(View view) {
//        if(view.getId() == R.id.viewpager){
//            //click to change application
//            AppId = (AppId + 1) % AppNames.length;
//            //getSupportActionBar().setTitle(AppNames[AppId]);
//            mTitle.setText(AppNames[AppId]);
//            for(int j = 0; j < NumItems; j++){
//                items[j].setImageResource(resourceIds[AppId]);
//                items[j].setIsSelected(false);
//            }
//            MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
//            for(PageFragment pf :  myAdapter.getPageFragments()){
//                pf.getAdapter().notifyDataSetChanged();
//            }
//        }
//    }

    public void updateUIs(){
        MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
        for(PageFragment pf :  myAdapter.getPageFragments()){
            pf.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        Log.i(TAG, "double tap");

        //click to change application
        AppId = (AppId + 1) % AppNames.length;
        //getSupportActionBar().setTitle(AppNames[AppId]);
        mTitle.setTextColor(appColors[AppId]);
        mTitle.setText(AppNames[AppId]);
        for(int j = 0; j < NumItems; j++){
            items[j].setImageResource(resourceIds[AppId]);
            items[j].setIsSelected(false);
        }
        MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
        for(PageFragment pf :  myAdapter.getPageFragments()){
            pf.getAdapter().notifyDataSetChanged();
        }

        return true;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        Log.d(TAG,"onDown: " + event.toString());
        return false;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        Log.d(TAG, "onFling: " + event1.toString() + event2.toString());
        return false;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        Log.d(TAG, "onLongPress: " + event.toString());
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                            float distanceY) {
        Log.d(TAG, "onScroll: " + event1.toString() + event2.toString());
        return false;
    }

    @Override
    public void onShowPress(MotionEvent event) {
        Log.d(TAG, "onShowPress: " + event.toString());
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        Log.d(TAG, "onSingleTapUp: " + event.toString());
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        Log.d(TAG, "onDoubleTapEvent: " + event.toString());
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        Log.d(TAG, "onSingleTapConfirmed: " + event.toString());
        return false;
    }


    public class MyPagerAdapter extends FragmentPagerAdapter {
        private List<PageFragment> mFragmentList = new ArrayList<>();
        private List<String> mFragmentTitleList = new ArrayList<>();

        public MyPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        public void addFragment(PageFragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        public List<PageFragment> getPageFragments(){
            return mFragmentList;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }
}


