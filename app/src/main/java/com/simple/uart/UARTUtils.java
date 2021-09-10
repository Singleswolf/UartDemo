package com.simple.uart;


import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android_serialport_api.SerialPort;


public class UARTUtils {
    private final String TAG = "UARTUtils";
    private static UARTUtils mInstace;
    private SerialPort serialPort = null;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread recvThread;
    private boolean read = false;
    private ReadCallback mCallback;

    private UARTUtils() {
    }

    public static UARTUtils getInstance() {
        if (mInstace == null) {
            synchronized (UARTUtils.class) {
                if (mInstace == null) {
                    mInstace = new UARTUtils();
                }
            }
        }
        return mInstace;
    }

    public synchronized void init(String DEVICE, int BAUDRATE, ReadCallback callback) {
        if (read) {
            Log.i(TAG, String.format("串口地址：%s, 波特率：%d, 已打开\n", DEVICE, BAUDRATE));
            return;
        }
        if (recvThread != null) {
            close();
        }

        mCallback = callback;
        if (recvThread == null) {
            if (serialPort == null) {
                try {
                    serialPort = new SerialPort(new File(DEVICE), BAUDRATE, 0);
                    inputStream = serialPort.getInputStream();
                    outputStream = serialPort.getOutputStream();
                    read = true;
                    Log.i(TAG, String.format("串口地址：%s, 波特率：%d, 打开成功\n", DEVICE, BAUDRATE));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    read = false;
                    Log.e(TAG, String.format("串口地址：%s, 波特率：%d, 打开失败，失败原因：%s", DEVICE, BAUDRATE, ex.getMessage()));
                    if (mCallback != null) {
                        mCallback.openFail(ex);
                    }
                }
            }
            if (!read) return;
            recvThread = new ReadThread();
            recvThread.setName("Read");
            recvThread.start();
        }
    }

    class ReadThread extends Thread {
        boolean interrupt;

        @Override
        public void interrupt() {
            super.interrupt();
            interrupt = true;
        }

        @Override
        public void run() {
            while (!interrupt) {
                int bytes = 0;
                int size;
                try {
                    if (inputStream.available() <= 0) {
                        continue;
                    } else {
                        Thread.sleep(100);
                    }
                    byte[] buffer = new byte[512];
                    byte[] read = new byte[64];
                    while ((size = inputStream.read(read)) != -1) {
                        System.arraycopy(read, 0, buffer, bytes, size);
                        bytes += size;
                        if (size != read.length) {
                            break;
                        }
                        Thread.sleep(50);//延时50ms
                    }
                    if (bytes > 0) {
                        if (mCallback != null) {
                            mCallback.onRead(Arrays.copyOfRange(buffer, 0, bytes));
                        }
                    }


                } catch (IOException e) {
                    Log.e(TAG, "IOException" + e.getMessage());
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Log.i(TAG, "ReadThread is break");
        }
    }


    public void sendData(byte[] data) {
        if (outputStream != null) {
            try {
                outputStream.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void close() {
        if (recvThread != null) {
            Log.i(TAG, "关闭ReadThread串口");
            recvThread.interrupt();
            recvThread = null;
        }

        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                inputStream = null;
            }
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                outputStream = null;
            }
        }
    }

    public synchronized void release() {
        read = false;
        mCallback = null;
        close();
    }

    public interface ReadCallback {
        void onRead(byte[] data);

        void openFail(Exception ex);
    }
}
