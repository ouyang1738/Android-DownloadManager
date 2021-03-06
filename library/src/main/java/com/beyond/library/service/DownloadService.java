package com.beyond.library.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;

import com.beyond.library.constants.Constants;
import com.beyond.library.constants.DownloadConfiguration;
import com.beyond.library.entity.DownloadConstants;
import com.beyond.library.entity.DownloadFileInfo;
import com.beyond.library.manager.CallbackManager;
import com.beyond.library.util.DownloadUtil;
import com.beyond.library.util.L;
import com.beyond.library.util.NetUtil;
import com.beyond.library.util.StorageUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * 后台下载Service
 */
public class DownloadService extends Service {

	private static final int MSG_INIT = 0;
	public int mThreadCount = 1;
	// 下载任务的集合
	private Map<String, DownloadTask> mTaskMap = new LinkedHashMap<String, DownloadTask>();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) {
			return -1;
		}
		// 获取从activity中传过来的参数
		DownloadFileInfo fileInfo = (DownloadFileInfo) intent.getSerializableExtra("fileInfo");
		if (fileInfo == null) {
			return -1;
		}
		if (Constants.ACTION_START.equals(intent.getAction())) {
			L.i("start-->fileInfo::" + fileInfo.toString());
			//判断网络状态,网络不可用时不可下载
			int netWorkType = NetUtil.getNetWorkType(this);
			if (netWorkType == DownloadConstants.NetType.INVALID) {
				// 网络不可用
				CallbackManager.getInstance().notifyInitFailure(fileInfo, "网络不可用");
			} else {
				// WiFi网络
				//开启初始化线程
				InitThread initThread = new InitThread(fileInfo);
				initThread.start();
			}
		} else if (Constants.ACTION_PAUSE.equals(intent.getAction())) {
			L.i("stop-->fileInfo::" + fileInfo.toString());
			// 从集合中取出下载任务
			DownloadTask downloadTask = mTaskMap.get(fileInfo.getUrl());
			if (downloadTask != null) {
				downloadTask.doPause();
			}
		} else if (Constants.ACTION_RESUME.equals(intent.getAction())) {
			L.i("resume-->fileInfo::" + fileInfo.toString());
			// 从集合中取出下载任务
			DownloadTask downloadTask = mTaskMap.get(fileInfo.getUrl());
			if (downloadTask == null) {
				// 新建下载任务
				downloadTask = new DownloadTask(DownloadService.this, fileInfo, 3);
				// 把下载任务添加到集合中
				mTaskMap.put(fileInfo.getUrl(), downloadTask);
			}
			downloadTask.doDownload();
		} else if (Constants.ACTION_CONFIRM.equals(intent.getAction())) {
			L.i("confirm-->fileInfo::" + fileInfo.toString());
			executeTask(fileInfo);
		}

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_INIT:
				DownloadFileInfo fileInfo = (DownloadFileInfo) msg.obj;
				// WIFI环境直接下载;运营商网络环境(2G/3G/4G)需要用户确认后才执行下载
				int netWorkType = NetUtil.getNetWorkType(DownloadService.this);
				if (netWorkType != DownloadConstants.NetType.WIFI && !DownloadConfiguration.CONFIRM_DOWNLOAD_IN_MOBILE_NET) {
					CallbackManager.getInstance().notifyMobileNetConfirm(fileInfo);
				} else {
					executeTask(fileInfo);
				}
				break;

			default:
				break;
			}
		}

	};

	/**
	 * 启动下载任务
	 */
	private void executeTask(DownloadFileInfo fileInfo) {
		if (mThreadCount == 0) {
			// 设置下载线程个数
			mThreadCount = DownloadUtil.getThreadCount(fileInfo.getLength());
		}
		// 启动下载任务
		DownloadTask downloadTask = new DownloadTask(DownloadService.this, fileInfo, mThreadCount);
		downloadTask.doDownload();
		// 把下载任务添加到集合中
		mTaskMap.put(fileInfo.getUrl(), downloadTask);
	}
	
	/**
	 * 判断源是否支持断点续传
	 */
	private boolean isAcceptRange(String urlStr) {
		HttpURLConnection conn = null;
		try{
			URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setAllowUserInteraction(true);
			conn.setRequestProperty("Range", "bytes=" + 0 + "-" + Integer.MAX_VALUE);

			// 设置连接超时时间为10000ms
			conn.setConnectTimeout(10000);

			// 设置读取数据超时时间为10000ms
			conn.setReadTimeout(10000);
			// 判断源是否支持断点续传
			if (conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL){
				L.i("该源不支持断点下载...");
				return false;
			}else{
				L.i("该源支持断点下载...");
				return true;
			}
		} catch (IOException e){
			e.printStackTrace();
			return false;
		}finally{
			if (conn!=null) {
				conn.disconnect();
			}
		}
	}


	/**
	 * 通过网络请求获取要下载文件的长度，并在本地创建一样大小的文件
	 * 
	 * @author Beyond
	 *
	 */
	class InitThread extends Thread {
		private DownloadFileInfo mFileInfo;

		public InitThread(DownloadFileInfo mFileInfo) {
			super();
			this.mFileInfo = mFileInfo;
		}

		@SuppressWarnings("deprecation")
		public void run() {
			HttpURLConnection conn = null;
			RandomAccessFile raf = null;
			try {
				// 1.链接网络文件
				String url = mFileInfo.getUrl();
				conn = NetUtil.buildConnection(url);
				int contentLength = -1;
				if (conn.getResponseCode() == 200) {
					contentLength = conn.getContentLength();
				}
				if (contentLength < 0) {
					CallbackManager.getInstance().notifyFailure(mFileInfo, "下载资源不存在!");
					return;
				}

				//2.获取手机存储空间,存储空间不足的情况下不可下载
				//存在SD Card
				L.d("【"+mFileInfo.getFileName()+"】文件大小："+DownloadUtil.formatFileSize(contentLength, false));
				if (StorageUtil.externalMemoryAvailable()) {
					//如果下载文件的大小大于SD剩余可用空间，下载无效
					L.d("SD Card剩余存储空间："+DownloadUtil.formatFileSize(StorageUtil.getAvailableExternalMemorySize(), false));
					if (contentLength > StorageUtil.getAvailableExternalMemorySize()) {
						CallbackManager.getInstance().notifyFailure(mFileInfo, "SD卡剩余空间不足！");
						return;
					}
				}else{
					L.d("手机内部剩余存储空间："+DownloadUtil.formatFileSize(StorageUtil.getAvailableInternalMemorySize(), false));
					if (contentLength > StorageUtil.getAvailableInternalMemorySize()) {
						CallbackManager.getInstance().notifyFailure(mFileInfo, "手机剩余空间不足！");
						return;
					}
				}
				
				//3.设置下载线程个数
				//若支持断点下载，则根据文件大小分配线程
				if (isAcceptRange(mFileInfo.getUrl())) {
					mThreadCount = DownloadUtil.getThreadCount(contentLength);
					mFileInfo.setAcceptRanges(true);
				}else{
					//若不支持断点下载，线程数为1
					mThreadCount = 1;
					mFileInfo.setAcceptRanges(false);
				}

				//4.创建下载路径
				String storagePath = mFileInfo.getStoragePath();
				if (TextUtils.isEmpty(storagePath)) {
					L.e("下载路径不存在");
					CallbackManager.getInstance().notifyFailure(mFileInfo, "下载路径不存在");
					return;
				}
				File dir = new File(storagePath);
				if (!dir.exists()) {
					dir.mkdir();
				}

				// 5.在本地创建文件
				File file = new File(dir, mFileInfo.getFileName());
				// 随机访问的文件，可以在文件的任意一个位置进行IO操作
				raf = new RandomAccessFile(file, "rwd");
				// 设置本地文件长度
				raf.setLength(contentLength);

				mFileInfo.setLength(contentLength);

				//6.初始化过程完毕
				mHandler.obtainMessage(MSG_INIT, mFileInfo).sendToTarget();
			} catch (Exception e) {
				L.e(e);
				CallbackManager.getInstance().notifyFailure(mFileInfo, "获取下载文件出错！");
			} finally {
				try {
					if (conn != null) {
						conn.disconnect();
					}
					if (raf != null) {
						raf.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}



}
