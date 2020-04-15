package com.chatbot01.camerafo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class StartActivity extends AppCompatActivity {

    private EditText editText;
    private Button btn_enter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        editText = findViewById(R.id.ip_text);
        btn_enter = findViewById(R.id.btn_enter);

        if(GlobalData.serverIP!=null) {
            editText.setHint(GlobalData.serverIP);
        }

        btn_enter.setOnClickListener(v ->{
            if(editText.getText()!=null) {
                GlobalData.serverIP = editText.getText().toString().trim();
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
            else
                editText.setText("請輸入IP");
        });

    }
}
