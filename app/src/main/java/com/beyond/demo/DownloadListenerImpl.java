package com.beyond.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.beyond.library.entity.DownloadFileInfo;
import com.beyond.library.listener.DownloadListener;
import com.beyond.library.util.DownloadUtil;
import com.beyond.library.util.L;


/**
 * Created by J.Beyond on 16/3/8.
 * Desc:
 */
public class DownloadListenerImpl implements DownloadListener {

    public static final int ERROR = 1000;
    public static final int NORMAL = 1001;
    public static final int MOBILE_NET_CONFIRM = 1002;
    private Context mContext;

    private Handler mHandler;

    public DownloadListenerImpl(Handler handler, Context context) {
        this.mHandler = handler;
        this.mContext = context;
    }

    @Override
    public void onDownloadWait(DownloadFileInfo fileInfo) {
        sendMessage(fileInfo);
    }

    @Override
    public void onDownloading(DownloadFileInfo fileInfo) {
        sendMessage(fileInfo);
    }

    @Override
    public void onDownloadPause(DownloadFileInfo fileInfo) {
        sendMessage(fileInfo);
    }

    @Override
    public void onDownloadFinished(DownloadFileInfo fileInfo) {
        DownloadUtil.installApk(mContext,fileInfo);
        sendMessage(fileInfo);
    }

    @Override
    public void onDownloadError(DownloadFileInfo fileInfo, String errorInfo) {
        L.e("下载失败:"+errorInfo);
        sendMessage(fileInfo);
    }

    @Override
    public void onMobileNetConfirm(DownloadFileInfo fileInfo) {
        Message msg = Message.obtain();
        msg.what = MOBILE_NET_CONFIRM;
        msg.obj = fileInfo;
        mHandler.sendMessage(msg);
    }

    private void sendMessage(DownloadFileInfo fileInfo) {
        Message msg = Message.obtain();
        msg.what = NORMAL;
        msg.obj = fileInfo;
        mHandler.sendMessage(msg);
    }
}
