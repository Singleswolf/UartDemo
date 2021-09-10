package com.simple.uart;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.method.NumberKeyListener;
import android.text.method.ScrollingMovementMethod;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.simple.uart.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android_serialport_api.SerialPortFinder;

@SuppressLint({"SetTextI18n", "SimpleDateFormat"})
public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";

    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
    private ActivityMainBinding binding;
    private final StringBuffer mStringBuffer = new StringBuffer();
    private boolean autoClear;
    private boolean autoSend;
    private boolean isHex;
    private int showLine;
    private Thread sendThread;
    private int lastCom = -1;
    private int lastPort = -1;
    private String cacheHex;
    private String cacheTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initListener();
        initViews();
    }

    private void initListener() {
        binding.btnClear.setOnClickListener(this);
        binding.btnSend.setOnClickListener(this);
        binding.tbOpen.setOnCheckedChangeListener(this);

        binding.rbTxt.setOnClickListener(this);
        binding.rbHex.setOnClickListener(this);

        binding.cbAutoClear.setOnCheckedChangeListener(this);
        binding.cbAutoSend.setOnCheckedChangeListener(this);

        binding.spComPort.setOnItemSelectedListener(this);
        binding.spBaudRate.setOnItemSelectedListener(this);
    }

    private void initViews() {
        SerialPortFinder finder = new SerialPortFinder();
        String[] devices = finder.getAllDevicesPath();
        List<String> allDevices = Arrays.asList(devices);

        ArrayAdapter<String> aspnDevices = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, allDevices);
        aspnDevices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spComPort.setAdapter(aspnDevices);
        binding.spComPort.setSelection(allDevices.size() - 1);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.baudrates_value, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spBaudRate.setAdapter(adapter);
        binding.spBaudRate.setSelection(12);

        binding.rbTxt.setChecked(true);
        binding.cbAutoClear.setChecked(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UARTUtils.getInstance().release();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_clear) {
            showLine = 0;
            mStringBuffer.delete(0, mStringBuffer.length());
            binding.etRecv.setText("");
            binding.etShowLine.setText(String.valueOf(showLine));
        } else if (id == R.id.btn_send) {
            String trim = binding.etSendData.getText().toString().trim();
            if (!TextUtils.isEmpty(trim)) {
                if (isHex) {
                    UARTUtils.getInstance().sendData(MyFunc.HexToByteArr(trim));
                } else {
                    UARTUtils.getInstance().sendData(trim.getBytes());
                }
            }
        } else if (v.getId() == R.id.rb_txt) {
            if (isHex) {
                cacheHex = binding.etSendData.getText().toString().trim();
                if (!TextUtils.isEmpty(cacheTxt)) {
                    binding.etSendData.setText(cacheTxt);
                } else {
                    binding.etSendData.setText("ComA");
                }
            }
            isHex = !binding.rbTxt.isChecked();

            KeyListener txtkeyListener = new TextKeyListener(TextKeyListener.Capitalize.NONE, false);
            binding.etSendData.setKeyListener(txtkeyListener);
        } else if (v.getId() == R.id.rb_hex) {
            if (!isHex) {
                cacheTxt = binding.etSendData.getText().toString().trim();
                if (!TextUtils.isEmpty(cacheHex)) {
                    binding.etSendData.setText(cacheHex);
                } else {
                    binding.etSendData.setText("AA");
                }
            }
            isHex = binding.rbHex.isChecked();

            KeyListener HexkeyListener = new NumberKeyListener() {
                public int getInputType() {
                    return InputType.TYPE_CLASS_TEXT;
                }

                @Override
                protected char[] getAcceptedChars() {
                    return new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                            'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'};
                }
            };
            binding.etSendData.setKeyListener(HexkeyListener);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.tb_open) {
            if (isChecked) {
                UARTUtils.getInstance().init(binding.spComPort.getSelectedItem().toString(),
                        Integer.parseInt(binding.spBaudRate.getSelectedItem().toString()), mReadCallback);
            } else {
                UARTUtils.getInstance().release();
            }
        } else if (buttonView.getId() == R.id.cb_auto_clear) {
            autoClear = isChecked;
        } else if (buttonView.getId() == R.id.cb_auto_send) {
            autoSend = isChecked;
            if (autoSend) {
                if (sendThread == null) {
                    sendThread = new SendThread();
                    sendThread.start();
                }
            } else {
                if (sendThread != null) {
                    sendThread.interrupt();
                    sendThread = null;
                }
            }
        }
    }

    private final UARTUtils.ReadCallback mReadCallback = new UARTUtils.ReadCallback() {
        @Override
        public void onRead(byte[] data) {
            try {
                String txt = new String(data, "GBK");
                String hex = MyFunc.ByteArrToHex(data);

                Log.d(TAG, "read size = " + data.length + ", str : " + txt);
                Log.d(TAG, "read byte: " + hex);
                runOnUiThread(() -> {
                    if (autoClear && showLine >= 500) {
                        mStringBuffer.delete(0, mStringBuffer.length());
                        showLine = 1;
                    } else {
                        showLine++;
                    }
                    binding.etShowLine.setText(String.valueOf(showLine));
                    mStringBuffer.append(String.format("[%s][recv][%s]%s\n", format.format(new Date()), isHex ? "Hex" : "Txt", isHex ? hex : txt));
                    binding.etRecv.setText(mStringBuffer);
                    binding.etRecv.setMovementMethod(ScrollingMovementMethod.getInstance());
                    binding.etRecv.setSelection(mStringBuffer.length(), mStringBuffer.length());
                });
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void openFail(Exception ex) {
            if (ex instanceof SecurityException) {
                ShowMessage("打开串口失败:没有串口读/写权限!");
            } else if (ex instanceof IOException) {
                ShowMessage("打开串口失败:未知错误!");
            } else if (ex instanceof InvalidParameterException) {
                ShowMessage("打开串口失败:参数错误!");
            }
        }
    };

    private void ShowMessage(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    class SendThread extends Thread {
        boolean interrupt;

        @Override
        public void interrupt() {
            super.interrupt();
            interrupt = true;
        }

        @Override
        public void run() {
            String data = binding.etSendData.getText().toString().trim();
            if (TextUtils.isEmpty(data)) {
                return;
            }
            String trim = binding.etTime.getText().toString().trim();
            int time = TextUtils.isEmpty(trim) ? 0 : Integer.parseInt(trim);
            while (!interrupt) {
                Log.d(TAG, "SendThread: " + data);
                if (isHex) {
                    UARTUtils.getInstance().sendData(MyFunc.HexToByteArr(data));
                } else {
                    UARTUtils.getInstance().sendData(data.getBytes());
                }
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    Log.e(TAG, "SendThread interrupt and break");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.sp_com_port) {
            if (lastCom != -1 && lastCom != position) {
                binding.tbOpen.setChecked(false);
            }
            lastCom = position;
        } else if (parent.getId() == R.id.sp_baud_rate) {
            if (lastPort != -1 && lastPort != position) {
                binding.tbOpen.setChecked(false);
            }
            lastPort = position;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}