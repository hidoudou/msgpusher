package com.doudou.lc.msgpusher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.doudou.lc.msgpusher.zxing.android.CaptureActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "medivh";
    private static final int REQUEST_CODE_SCAN = 0x0000;
    private static final String DECODED_CONTENT_KEY = "codedContent";
    private static final String DECODED_BITMAP_KEY = "codedBitmap";

    private static final int SHOW_SEND_RESPONSE = 10000;
    private static final int SHOW_LOCAL_FCM_TOKEN = 10001;
    private static final int SHOW_REMOTE_FCM_TOKEN = 10002;
    private static final int SHOW_SERVER_KEY = 10003;

    private Button get_fcm_token_btn;
    private Button send_hello_msg_btn;

    private Button import_fcm_token_btn;
    private Button import_server_key_btn;

    private TextView token_tv;

    private String remote_fcm_token = null;
    private String server_key = null;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SHOW_SEND_RESPONSE:
                    token_tv.setText((String)msg.obj);
                    super.handleMessage(msg);
                    break;
                case SHOW_LOCAL_FCM_TOKEN:
                    //生成二维码
                    String localToken = (String)msg.obj;
                    Log.d(TAG, "local token:" + localToken);
                    Bitmap testImg = QRCodeUtil.createQRCodeBitmap(localToken, 480, 480);
                    ImageSpan imgSpan = new ImageSpan(getApplicationContext(), testImg);
                    //ImageSpan imgSpan = new ImageSpan(this, R.drawable.ic_launcher);
                    SpannableString spanString = new SpannableString(" ");
                    spanString.setSpan(imgSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    token_tv.setText(spanString);
            }

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);

        Log.d(TAG, "begin--------------------------");
        token_tv = (TextView)findViewById(R.id.token_tv);

        get_fcm_token_btn = (Button)findViewById(R.id.get_fcm_token_btn);
        get_fcm_token_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get token
                FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = task.getResult().getToken();

                        // Log and toast
                        String msg = getString(R.string.msg_token_fmt, token);
                        Log.d(TAG, msg);
                        //Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        //token_tv.setText(token);

                        Message msg4token = new Message();
                        msg4token.what = SHOW_LOCAL_FCM_TOKEN;
                        msg4token.obj = token;
                        mHandler.sendMessage(msg4token);
                    }
                });
            }
        });

        send_hello_msg_btn = (Button)findViewById(R.id.send_hello_msg_btn);
        send_hello_msg_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //send hello
                try {
                    run(mHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        import_fcm_token_btn = (Button)findViewById(R.id.import_fcm_token_btn);
        import_server_key_btn = (Button)findViewById(R.id.import_server_key_btn);

        import_server_key_btn.setOnClickListener(this);
        import_fcm_token_btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.import_server_key_btn:
            case R.id.import_fcm_token_btn:
                //动态权限申请
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, v.getId());
                } else {
                    goScan(v.getId());
                }
                break;
            default:
                break;
        }
    }

    /**
     * 跳转到扫码界面扫码
     */
    private void goScan(int btnID){
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        int reqCode = 0;
        if(btnID == R.id.import_server_key_btn) {
            reqCode = SHOW_SERVER_KEY;
        } else if(btnID == R.id.import_fcm_token_btn) {
            reqCode = SHOW_REMOTE_FCM_TOKEN;
        }
        startActivityForResult(intent, reqCode/*REQUEST_CODE_SCAN*/);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    goScan(requestCode);
                } else {
                    Toast.makeText(this, "你拒绝了权限申请，可能无法打开相机扫码哟！", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 扫描二维码/条码回传
        if(resultCode == RESULT_OK) {
            String content = data.getStringExtra(DECODED_CONTENT_KEY);
            Log.d(TAG, "content got:" + content);
            //返回的BitMap图像
            Bitmap bitmap = data.getParcelableExtra(DECODED_BITMAP_KEY);

            if(requestCode == SHOW_SERVER_KEY) {
                server_key = content;
            }else if(requestCode == SHOW_REMOTE_FCM_TOKEN) {
                remote_fcm_token = content;
            }
        }
    }


    public static final MediaType MEDIA_TYPE_MARKDOWN
            = MediaType.parse("application/json;charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    public void run(final Handler handler) throws Exception {
        String postBody = " {\n" +
                "    \"to\" : \"ejdqGZVJ60I:APA91bFKAvhRLBQcQoRhy38YgzB-DA781UpA-ImhoTDJWGEEinM4MdAKEoUIhV0hDfb3O1duTEQcd4JrOOPibs_SloWLLOUUsA9pNlKqSxAukWAUoeu3UKMROFNfqj2kD7V-6it2t7kG\",\n" +
                "    \"notification\" : {\n" +
                "      \"body\" : \"great match!\",\n" +
                "      \"title\" : \"2018-12-29\",\n" +
                "    }\n" +
                "  }";


        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("to", remote_fcm_token);

        JsonObject jsonElement = new JsonObject();
        jsonElement.addProperty("body", "test msg");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        jsonElement.addProperty("title", format.format(System.currentTimeMillis()));

        jsonObject.add("notification", jsonElement);


        Log.d("medivh", "body:\n" + jsonObject.toString());

        Request request = new Request.Builder()
                .url("https://fcm.googleapis.com/fcm/send")
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Authorization", "key="+server_key)
                .post(RequestBody.create(MEDIA_TYPE_MARKDOWN, jsonObject.toString()))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String a = response.body().string();
                Log.i("response", "run: " + a);
                {
                    //send response
                    Message msg = new Message();
                    msg.what = SHOW_SEND_RESPONSE;
                    msg.arg1 = 1;
                    msg.arg2 = 2;
                    msg.obj = a;

                    mHandler.sendMessage(msg);
                }
                //Toast.makeText(MainActivity.this, a, Toast.LENGTH_SHORT).show();
            }
        });

    }
}
