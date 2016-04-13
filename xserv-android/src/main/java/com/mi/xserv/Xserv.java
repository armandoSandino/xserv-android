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

import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Xserv extends XservBase {
    // op
    public final static int OP_HANDSHAKE = 100;
    public final static int OP_PUBLISH = 200;
    public final static int OP_SUBSCRIBE = 201;
    public final static int OP_UNSUBSCRIBE = 202;
    public final static int OP_HISTORY = 203;
    public final static int OP_USERS = 204;
    public final static int OP_TOPICS = 205;
    public final static int OP_UPDATE = 300;
    public final static int OP_JOIN = 401;
    public final static int OP_LEAVE = 402;
    // op result_code
    public final static int RC_OK = 1;
    public final static int RC_GENERIC_ERROR = 0;
    public final static int RC_ARGS_ERROR = -1;
    public final static int RC_ALREADY_SUBSCRIBED = -2;
    public final static int RC_UNAUTHORIZED = -3;
    public final static int RC_NO_TOPIC = -4;
    public final static int RC_NO_DATA = -5;
    public final static int RC_NOT_PRIVATE = -6;
    public final static int RC_LIMIT_MESSAGES = -7;
    public final static int RC_DB_ERROR = -8;

    private final static String TAG = "Xserv";
    private final static String VERSION = "1";
    // private final static String HOST = "192.168.130.187";
    private final static String HOST = "mobile-italia.com";
    private final static String PORT = "4332";
    private final static String TLS_PORT = "8332";
    private final static String URL = "ws%1$s://%2$s:%3$s/ws/%4$s?version=%5$s";
    private final static String DEFAULT_AUTH_URL = "http%1$s://%2$s:%3$s/1/user";
    private final static int DEFAULT_RI = 5000;
    // callbacks
    private static HashMap<String, OnCompletionListener> mCallbacks;
    // attributes
    private String mAppId;
    private Future<WebSocket> mConn;
    private int mReconnectInterval;
    private boolean isAutoReconnect;
    private JSONObject mUserData;
    private boolean isConnected;
    private boolean isSecure;

    /**
     * Return an instance of Xserv connector.
     *
     * @param app_id identifier of your application. You can find it on Xserv Dashboard.
     */
    public Xserv(String app_id) {
        super();

        mAppId = app_id;
        mConn = null;
        mUserData = new JSONObject();
        mReconnectInterval = DEFAULT_RI;

        isAutoReconnect = false;
        isConnected = false;

        // TLS
        isSecure = true;

        // Callbacks
        mCallbacks = new HashMap<>();
    }

    public static boolean isPrivateTopic(String topic) {
        return topic.startsWith("@");
    }

    public void disableTLS() {
        isSecure = false;
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
            String protocol = "";
            String port = PORT;
            if (isSecure) {
                protocol = "s";
                port = TLS_PORT;
            }

            mCallbacks.clear();

            AsyncHttpClient as = AsyncHttpClient.getDefaultInstance();
            mConn = as.websocket(String.format(
                    URL, protocol, HOST, port, mAppId, VERSION), null,
                    new AsyncHttpClient.WebSocketConnectCallback() {

                        @Override
                        public void onCompleted(Exception e, WebSocket ws) {
                            if (e == null) {
                                setOtherWsCallback(ws);

                                handshake();
                            } else {
                                mCallbacks.clear();

                                onErrorConnection(e);

                                // eccezione, error socket in connection non e' la error
                                reConnect();
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

                mCallbacks.clear();

                onCloseConnection(e);

                reConnect();
            }
        });

        ws.setStringCallback(new WebSocket.StringCallback() {

            @Override
            public void onStringAvailable(String event) {
                manageMessage(event);
            }
        });
    }

    private void manageMessage(String event) {
        JSONObject json = null;
        try {
            json = new JSONObject(event);
        } catch (JSONException ignored) {
        }

        if (json != null) {
            int op = 0;
            try {
                op = json.getInt("op");
            } catch (JSONException ignored) {
            }

            if (op == 0) {
                // messages

                onReceiveMessages(json);
            } else if (op > 0) {
                // operations

                try {
                    String data = json.getString("data");
                    byte[] b = Base64.decode(data, Base64.DEFAULT);
                    json.put("data", new String(b, "UTF-8")); // string
                } catch (JSONException | UnsupportedEncodingException ignored) {
                }

                JSONObject json_data = null;
                try {
                    String data = json.getString("data");
                    Object type = new JSONTokener(data).nextValue();
                    if (type instanceof JSONObject) {
                        json_data = new JSONObject(data);

                        json.put("data", json_data);
                    } else if (type instanceof JSONArray) {
                        json.put("data", new JSONArray(data));
                    }
                } catch (JSONException ignored) {
                }

                try {
                    json.put("name", stringifyOp(op));
                } catch (JSONException ignored) {
                }

                int rc = 0;
                String uuid = "";
                String topic = "";
                String descr = "";
                try {
                    rc = json.getInt("rc");
                    uuid = json.getString("uuid");
                    topic = json.getString("topic");
                    descr = json.getString("descr");
                } catch (JSONException ignored) {
                }

                if (op == OP_HANDSHAKE) {
                    // handshake

                    if (rc == RC_OK) {
                        if (json_data != null) {
                            setUserData(json_data);

                            isConnected = true;

                            onOpenConnection();
                        } else {
                            mCallbacks.clear();

                            onErrorConnection(new Exception(descr));
                        }
                    } else {
                        mCallbacks.clear();

                        onErrorConnection(new Exception(descr));
                    }
                } else {
                    // classic operations

                    if (op == OP_SUBSCRIBE && isPrivateTopic(topic) && rc == RC_OK) {
                        if (json_data != null) {
                            setUserData(json_data);
                        }
                    }

                    if (mCallbacks.get(uuid) != null) {
                        mCallbacks.get(uuid).onCompletion(json);
                        mCallbacks.remove(uuid);
                    } else {
                        OnReceiveOperations(json);
                    }
                }
            }
        }
    }

    private void reConnect() {
        if (isAutoReconnect && mReconnectInterval > 0) {
            getMainLooper().postDelayed(new Runnable() {

                @Override
                public void run() {
                    connect(true);
                }
            }, mReconnectInterval);
        }
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

    private void handshake() {
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
            stat.put("lang", getLanguage());
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

        if (op == OP_SUBSCRIBE && isPrivateTopic(topic)) {
            JSONObject auth = null;
            try {
                auth = json.getJSONObject("auth");
            } catch (JSONException ignored) {
            }

            if (auth != null) {
                String protocol = "";
                String port = PORT;
                if (isSecure) {
                    protocol = "s";
                    port = TLS_PORT;
                }

                // add url encode params
                String url_encode = "";
                String user = "";
                String pass = "";
                try {
                    JSONObject params = auth.getJSONObject("params");
                    Iterator<?> keys = params.keys();
                    while (keys.hasNext()) {
                        String name = (String) keys.next();
                        String value = (String) params.get(name);

                        switch (name) {
                            case "user":
                                user = value;
                                break;
                            case "pass":
                                pass = value;
                                break;
                            default:
                                try {
                                    if (url_encode.length() == 0) {
                                        url_encode += "?";
                                    } else {
                                        url_encode += "&";
                                    }
                                    url_encode += name + "=" + URLEncoder.encode(value, "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }

                    try {
                        if (url_encode.length() == 0) {
                            url_encode += "?";
                        } else {
                            url_encode += "&";
                        }
                        url_encode += "socket_id=" + URLEncoder.encode(getSocketId(), "UTF-8") +
                                "&topic=" + URLEncoder.encode(topic, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } catch (JSONException ignored) {
                }

                String endpoint = String.format(DEFAULT_AUTH_URL, protocol, HOST, port);
                try {
                    endpoint = auth.getString("endpoint");
                } catch (JSONException ignored) {
                }
                endpoint += url_encode;

                // Log.d(TAG, endpoint);
                AsyncHttpRequest request = new AsyncHttpRequest(Uri.parse(endpoint), "GET");

                // add custom headers
                try {
                    JSONObject headers = auth.getJSONObject("headers");
                    Iterator<?> keys = headers.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        request.setHeader(key, (String) headers.get(key));
                    }
                } catch (JSONException ignored) {
                }
                request.setHeader("X-Xserv-AppId", mAppId);

                if (user.length() > 0 && pass.length() > 0) {
                    byte[] user_pass = (user + ":" + pass).getBytes();
                    request.setHeader("Authorization", "basic " +
                            Base64.encodeToString(user_pass, Base64.NO_WRAP));
                }

                final String userStatic = user;

                AsyncHttpClient as = AsyncHttpClient.getDefaultInstance();
                as.executeJSONObject(request, new AsyncHttpClient.JSONObjectCallback() {
                    @Override
                    public void onCompleted(Exception e, AsyncHttpResponse source, JSONObject result) {
                        if (e == null) {
                            json.remove("auth");

                            try {
                                json.put("arg1", userStatic);
                                json.put("arg2", result.getString("data"));
                                json.put("arg3", result.getString("sign"));
                            } catch (JSONException ignored) {
                            }

                            wsSend(json);
                        } else {
                            json.remove("auth");

                            wsSend(json);
                        }
                    }
                });
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

    public String getSocketId() {
        String socket_id = "";
        try {
            socket_id = mUserData.getString("socket_id");
        } catch (JSONException ignored) {
            // e.printStackTrace();
        }
        return socket_id;
    }

    public JSONObject getUserData() {
        return mUserData;
    }

    private void setUserData(JSONObject json) {
        mUserData = json;
    }

    private String stringifyOp(int code) {
        switch (code) {
            case OP_SUBSCRIBE:
                return "subscribe";
            case OP_UNSUBSCRIBE:
                return "unsubscribe";
            case OP_HISTORY:
                return "history";
            case OP_USERS:
                return "users";
            case OP_JOIN:
                return "join";
            case OP_LEAVE:
                return "leave";
            case OP_PUBLISH:
                return "publish";
            case OP_HANDSHAKE:
                return "handshake";
            case OP_TOPICS:
                return "topics";
            case OP_UPDATE:
                return "update";
        }
        return "";
    }

    public String publish(String topic, Object data) {
        return publish(topic, data, null);
    }

    public String publish(String topic, Object data, OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_PUBLISH);
            json.put("topic", topic);
            json.put("arg1", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String update(String topic, String object_id, Object data) {
        return update(topic, object_id, data, null);
    }

    public String update(String topic, String object_id, Object data, OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_UPDATE);
            json.put("topic", topic);
            json.put("arg1", data);
            json.put("arg2", object_id);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String subscribe(String topic) {
        return subscribe(topic, null, null);
    }

    public String subscribe(String topic, OnCompletionListener listener) {
        return subscribe(topic, null, listener);
    }

    public String subscribe(String topic, JSONObject auth) {
        return subscribe(topic, auth, null);
    }

    public String subscribe(String topic, JSONObject auth, OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_SUBSCRIBE);
            json.put("topic", topic);
            if (auth != null) {
                json.put("auth", auth);
            }
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String unsubscribe(String topic) {
        return unsubscribe(topic, null);
    }

    public String unsubscribe(String topic, OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_UNSUBSCRIBE);
            json.put("topic", topic);
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String history(String topic, JSONObject params) {
        return history(topic, params, null);
    }

    public String history(String topic, JSONObject params, OnCompletionListener listener) {
        if (!isConnected()) return "";

        int offset = 0;
        int limit = 0;
        JSONObject query = null;
        if (params != null) {
            try {
                offset = params.getInt("offset");
            } catch (JSONException ignored) {
            }
            try {
                limit = params.getInt("limit");
            } catch (JSONException ignored) {
            }
            try {
                query = params.getJSONObject("query");
            } catch (JSONException ignored) {
            }
        }

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_HISTORY);
            json.put("topic", topic);
            json.put("arg1", String.valueOf(offset));
            json.put("arg2", String.valueOf(limit));
            if (query != null) {
                json.put("arg3", query);
            } else {
                json.put("arg3", "");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String users(String topic) {
        return users(topic, null);
    }

    public String users(String topic, OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_USERS);
            json.put("topic", topic);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public String topics() {
        return topics(null);
    }

    public String topics(OnCompletionListener listener) {
        if (!isConnected()) return "";

        String uuid = UUID.randomUUID().toString();
        if (listener != null) {
            mCallbacks.put(uuid, listener);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("uuid", uuid);
            json.put("op", OP_TOPICS);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(json);
        return uuid;
    }

    public interface OnCompletionListener {

        void onCompletion(JSONObject json);

    }

    public interface OnXservEventListener {

        void OnOpenConnection();

        void OnCloseConnection(Exception e);

        void OnErrorConnection(Exception e);

        void OnReceiveMessages(JSONObject json);

        void OnReceiveOperations(JSONObject json);

    }

}
