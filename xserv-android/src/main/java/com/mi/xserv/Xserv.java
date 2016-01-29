/***
 * Xserv
 * <p/>
 * Copyright (C) 2015 Giovanni Amati
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see http://www.gnu.org/licenses/.
 ***/

package com.mi.xserv;

import android.os.Build;
import android.util.Base64;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.mi.xserv.http.ITaskListener;
import com.mi.xserv.http.SimpleHttpRequest;
import com.mi.xserv.http.SimpleHttpTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Xserv extends XservBase {
    // events:op op
    public final static int TRIGGER = 200;
    public final static int BIND = 201;
    public final static int UNBIND = 202;
    public final static int HISTORY = 203;
    public final static int PRESENCE = 204;
    // in uso in presence
    public final static int PRESENCE_IN = BIND + 200;
    public final static int PRESENCE_OUT = UNBIND + 200;
    // in uso in history
    public final static String HISTORY_ID = "id";
    public final static String HISTORY_TIMESTAMP = "timestamp";
    // events:op result_code
    public final static int RC_OK = 1;
    public final static int RC_GENERIC_ERROR = 0;
    public final static int RC_ARGS_ERROR = -1;
    public final static int RC_ALREADY_BINDED = -2;
    public final static int RC_UNAUTHORIZED = -3;
    public final static int RC_NO_EVENT = -4;
    public final static int RC_NO_DATA = -5;
    public final static int RC_NOT_PRIVATE = -6;

    private final static String TAG = "Xserv";
    private final static String ADDRESS = "192.168.130.153";
    // private final static String ADDRESS = "mobile-italia.com";
    private final static String PORT = "4332";
    private final static String URL = "ws://%1$s:%2$s/ws/%3$s";
    private final static String DEFAULT_AUTH_URL = "http://%1$s:%2$s/app/%3$s/auth_user";
    private final static int DEFAULT_RI = 5000;

    // attributes
    private final String mAppId;
    private Future<WebSocket> mConn;
    private int mReconnectInterval;
    private boolean isAutoReconnect;
    private JSONObject mUserData;
    private boolean isConnected;

    public Xserv(String app_id) {
        super();

        mAppId = app_id;
        mConn = null;
        mUserData = new JSONObject();
        mReconnectInterval = DEFAULT_RI;

        isAutoReconnect = false;
        isConnected = false;
    }

    public static boolean isPrivateTopic(String topic) {
        return topic.startsWith("@");
    }

    public boolean isConnected() {
        return mConn != null && isConnected;
    }

    public void connect() {
        connect(false);
    }

    private void connect(boolean no_ar) {
        if (!no_ar) {
            isAutoReconnect = true;
        }

        if (!isConnected()) {
            AsyncHttpClient as = AsyncHttpClient.getDefaultInstance();
            mConn = as.websocket(String.format(URL, ADDRESS, PORT, mAppId), null,
                    new AsyncHttpClient.WebSocketConnectCallback() {

                        @Override
                        public void onCompleted(Exception e, WebSocket ws) {
                            if (e == null) {
                                setOtherWsCallback(ws);

                                sendStat();

                                isConnected = true;

                                onOpenConnection();
                            } else {
                                onErrorConnection(e);

                                // eccezione, error socket
                                if (isAutoReconnect) {
                                    reConnect();
                                }
                            }
                        }
                    });
        }
    }

    private void setOtherWsCallback(WebSocket ws) {
        ws.setClosedCallback(new CompletedCallback() {

            @Override
            public void onCompleted(Exception e) {
                isConnected = false;

                onCloseConnection(e);

                if (isAutoReconnect) {
                    reConnect();
                }
            }
        });

        ws.setStringCallback(new WebSocket.StringCallback() {

            @Override
            public void onStringAvailable(String event) {
                JSONObject json = null;
                try {
                    json = new JSONObject(event);
                } catch (JSONException ignored) {
                }

                if (json != null) {
                    int op = 0;
                    String message = null;
                    try {
                        op = json.getInt("op");
                    } catch (JSONException ignored) {
                    }
                    try {
                        message = json.getString("message");
                    } catch (JSONException ignored) {
                    }

                    if (message != null) {
                        try {
                            json.put("message", new JSONObject(message));
                        } catch (JSONException ignored) {
                        }

                        onReceiveEvents(json);
                    } else if (op > 0) {
                        int rc = 0;
                        String topic = "";
                        try {
                            rc = json.getInt("rc");
                            topic = json.getString("topic");

                            json.put("name", stringifyOp(op));
                        } catch (JSONException ignored) {
                        }

                        try {
                            String data = json.getString("data");
                            byte[] b = Base64.decode(data, Base64.DEFAULT);
                            String raw = new String(b, "UTF-8");
                            Object j = new JSONTokener(raw).nextValue();
                            if (j instanceof JSONObject) {
                                JSONObject data_json = new JSONObject(raw);
                                json.put("data", data_json);

                                // bind privata ok
                                if (op == BIND && isPrivateTopic(topic) && rc == RC_OK) {
                                    setUserData(data_json);
                                }
                            } else if (j instanceof JSONArray) {
                                json.put("data", new JSONArray(raw));
                            }
                        } catch (JSONException | UnsupportedEncodingException ignored) {
                        }

                        onReceiveOpsResponse(json);
                    }
                }
            }
        });
    }

    private void reConnect() {
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                connect(true);
            }
        }, mReconnectInterval);
    }

    public void disconnect() {
        isAutoReconnect = false;

        if (isConnected()) {
            try {
                mConn.get().close();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public Integer getReconnectInterval() {
        return mReconnectInterval;
    }

    public void setReconnectInterval(Integer milliseconds) {
        mReconnectInterval = milliseconds;
    }

    private void sendStat() {
        JSONObject stat = new JSONObject();
        try {
            String model = Build.MODEL;
            if (model.length() > 45) {
                model = model.substring(0, 45);
            }

            stat.put("uuid", getDeviceID());
            stat.put("model", model);
            stat.put("os", "Android " + Build.VERSION.RELEASE);
            stat.put("tz_offset", getTimeZoneOffset());
            stat.put("tz_dst", getTimeZoneDst());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        wsSend(stat);
    }

    private void send(final JSONObject json) {
        if (!isConnected()) return;

        int op = 0;
        String topic = "";
        try {
            op = json.getInt("op");
            topic = json.getString("topic");
        } catch (JSONException ignored) {
        }

        if (op == BIND && isPrivateTopic(topic)) {
            JSONObject auth_endpoint = null;
            try {
                auth_endpoint = json.getJSONObject("auth_endpoint");
            } catch (JSONException ignored) {
            }

            if (auth_endpoint != null) {
                String auth_url = String.format(DEFAULT_AUTH_URL, ADDRESS, PORT, mAppId);
                String auth_user = "";
                String auth_pass = "";
                try {
                    auth_url = auth_endpoint.getString("endpoint");
                } catch (JSONException ignored) {
                }
                try {
                    auth_user = auth_endpoint.getString("user");
                    auth_pass = auth_endpoint.getString("pass");
                } catch (JSONException ignored) {
                }

                final SimpleHttpRequest request =
                        new SimpleHttpRequest(SimpleHttpRequest.POST, auth_url);
                request.setContentType("application/json; charset=UTF-8");
                request.setParam("topic", topic);
                request.setParam("user", auth_user);
                request.setParam("pass", auth_pass);

                SimpleHttpTask task = new SimpleHttpTask();

                task.setOnResponseListener(new ITaskListener.OnResponseListener() {

                    @Override
                    public void onResponse(String output) {
                        JSONObject new_json = null;
                        try {
                            new_json = new JSONObject(json.toString()); // clone
                            new_json.remove("auth_endpoint");
                        } catch (JSONException ignored) {
                        }

                        if (new_json != null) {
                            try {
                                JSONObject data_sign = new JSONObject(output);
                                new_json.put("arg1", request.getParam("user"));
                                new_json.put("arg2", data_sign.getString("data"));
                                new_json.put("arg3", data_sign.getString("sign"));
                            } catch (JSONException ignored) {
                            }

                            wsSend(new_json);
                        } else {
                            // like fail
                            wsSend(json);
                        }
                    }

                    @Override
                    public void onFail() {
                        wsSend(json);
                    }
                });

                task.execute(request);
            } else {
                wsSend(json);
            }
        } else {
            wsSend(json);
        }
    }

    private void wsSend(JSONObject json) {
        try {
            mConn.get().send(json.toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getUserData() {
        return mUserData;
    }

    private void setUserData(JSONObject json) {
        mUserData = json;
    }

    private String stringifyOp(int code) {
        switch (code) {
            case BIND:
                return "bind";
            case UNBIND:
                return "unbind";
            case HISTORY:
                return "history";
            case PRESENCE:
                return "presence";
            case PRESENCE_IN:
                return "presence_in";
            case PRESENCE_OUT:
                return "presence_out";
            case TRIGGER:
                return "trigger";
        }
        return "";
    }

    public String trigger(String topic, String event, JSONObject message) {
        return trigger(topic, event, message.toString());
    }

    public String trigger(String topic, String event, String message) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", TRIGGER);
            data.put("topic", topic);
            data.put("event", event);
            data.put("arg1", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(data);
        return uuid;
    }

    public String bind(String topic, String event) {
        return bind(topic, event, null);
    }

    public String bind(String topic, String event, JSONObject auth_endpoint) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", BIND);
            data.put("topic", topic);
            data.put("event", event);
            if (auth_endpoint != null) {
                data.put("auth_endpoint", auth_endpoint);
            }
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        send(data);
        return uuid;
    }

    public String unbind(String topic) {
        return unbind(topic, "");
    }

    public String unbind(String topic, String event) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", UNBIND);
            data.put("topic", topic);
            data.put("event", event);
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        send(data);
        return uuid;
    }

    public String historyById(String topic, String event, Integer offset) {
        return historyById(topic, event, offset, 0);
    }

    public String historyById(String topic, String event, Integer offset, Integer limit) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", HISTORY);
            data.put("topic", topic);
            data.put("event", event);
            data.put("arg1", HISTORY_ID);
            data.put("arg2", String.valueOf(offset));
            data.put("arg3", String.valueOf(limit));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(data);
        return uuid;
    }

    public String historyByTimestamp(String topic, String event, Integer offset) {
        return historyByTimestamp(topic, event, offset, 0);
    }

    public String historyByTimestamp(String topic, String event, Integer offset, Integer limit) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", HISTORY);
            data.put("topic", topic);
            data.put("event", event);
            data.put("arg1", HISTORY_TIMESTAMP);
            data.put("arg2", String.valueOf(offset));
            data.put("arg3", String.valueOf(limit));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(data);
        return uuid;
    }

    public String presence(String topic, String event) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        JSONObject data = new JSONObject();
        try {
            data.put("uuid", uuid);
            data.put("op", PRESENCE);
            data.put("topic", topic);
            data.put("event", event);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(data);
        return uuid;
    }

}
