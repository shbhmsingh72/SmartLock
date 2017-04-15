package com.shubham.lockscreen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends Activity {

    String mobileArray[] = new String[]{"Wink Left Eye","Wink Right Eye","Say Cheese!", "Cover Half Face"};
    private static int count=0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                R.layout.activity_listview, mobileArray);

        ListView listView = (ListView) findViewById(R.id.mainList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long arg3) {
                view.setSelected(true);
                SharedPreferences prefs = getSharedPreferences(
                        "com.shubham.lockscreen", Context.MODE_PRIVATE);
                String Choice="Choice";
                prefs.edit().putInt(Choice, position).apply();
                if(count==0) {
                    count=1;
                    Intent i = new Intent(MainActivity.this, LockScreenActivity.class);
                    i.putExtra("Choice", position);
                    startActivity(i);
                }
                //Anything
            }
        });

    }
}
