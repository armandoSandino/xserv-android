package com.mi.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.mi.xserv.Xserv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements Xserv.OnXservEventListener {
    private final static String TAG = "MainActivity";
    private final static String APP_ID = "9Pf80-3";
    private Xserv mXserv;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private MyAdapter mAdapter;
    private ArrayList<JSONObject> mDataSource;
    private String mRoomName;
    private HashMap<String, JSONObject> mUsers;
    private EditText mEditText;

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString("roomName", mRoomName);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        setTitle(getString(R.string.app_name_full));

        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        if (mRecyclerView != null) {
            mRecyclerView.setHasFixedSize(true);
        }

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mDataSource = new ArrayList<>();
        mUsers = new HashMap<>();

        // specify an adapter (see also next example)
        mAdapter = new MyAdapter(mDataSource);
        mRecyclerView.setAdapter(mAdapter);

        mEditText = (EditText) findViewById(R.id.editText);
        if (mEditText != null) {
            mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    boolean handled = false;
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        sendMessage(null);
                        handled = true;
                    }
                    return handled;
                }
            });
        }

        if (savedInstanceState != null) {
            mRoomName = savedInstanceState.getString("roomName");
        } else {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                mRoomName = b.getString("roomName");
            }
        }

        mXserv = new Xserv(APP_ID);
        // mXserv.disableTLS();

        mXserv.setOnEventListener(this);

        mXserv.connect();
    }

    public void sendMessage(View view) {
        String message = mEditText.getText().toString();

        Object data = message;
        try {
            Object type = new JSONTokener(message).nextValue();
            if (type instanceof JSONObject) {
                data = new JSONObject(message);
            } else if (type instanceof JSONArray) {
                data = new JSONArray(message);
            }
        } catch (JSONException ignored) {
        }

        if (message.length() > 0) {
            mXserv.publish(mRoomName, data);

            mEditText.setText("");
        }
    }

    @Override
    public void OnOpenConnection() {
        Log.d(TAG, "Connected");

        mXserv.subscribe(mRoomName);
    }

    @Override
    public void OnCloseConnection(Exception e) {
        Log.w(TAG, "Disconnected");
    }

    @Override
    public void OnErrorConnection(Exception e) {
        e.printStackTrace();
    }

    @Override
    public void OnReceiveMessages(final JSONObject json) {
        JSONObject stat = null;
        try {
            JSONObject user = json.getJSONObject("user");
            stat = user.getJSONObject("stat");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (stat != null) {
            String os = "";
            try {
                os = stat.getString("os");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            int os_res = -1;
            if (os.startsWith("And")) {
                os_res = R.drawable.and_icon;
            } else if (os.startsWith("iOS")) {
                os_res = R.drawable.ios_icon;
            } else if (os.startsWith("Browser")) {
                os_res = R.drawable.js_icon;
            } else if (os.startsWith("API")) {
                os_res = R.drawable.api_icon;
            }

            try {
                json.put("os_res", os_res);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                json.put("os_res", R.drawable.oth_icon);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mDataSource.add(0, json);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void OnReceiveOperations(JSONObject json) {
        int op = 0;
        try {
            op = json.getInt("op");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void finish() {
        super.finish();

        mXserv.disconnect();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(menuItem);
    }

}
