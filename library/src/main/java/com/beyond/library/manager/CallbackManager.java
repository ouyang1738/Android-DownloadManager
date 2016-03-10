package com.beyond.library.manager;

import android.text.TextUtils;

import com.beyond.library.entity.DownloadFileInfo;
import com.beyond.library.entity.DownloadState;
import com.beyond.library.entity.SpeedInfo;
import com.beyond.library.listener.DownloadInitListener;
import com.beyond.library.listener.DownloadListener;
import com.beyond.library.util.DownloadUtil;
import com.beyond.library.util.L;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下载回调管理类
 */
public class CallbackManager {

	private static final String TAG = "CallbackManager";
	private Map<String, List<DownloadListener>> mCallbackMap = new ConcurrentHashMap<>();
	private Map<String, DownloadInitListener> mInitCallbackMap = new ConcurrentHashMap<>();
	private Map<String, SpeedInfo> mSpeedMap = new ConcurrentHashMap<>();
	private static CallbackManager mManager;

	private CallbackManager() {
	}

	public static CallbackManager getInstance() {
		if (mManager == null) {
			synchronized (CallbackManager.class) {
				if (mManager == null) {
					mManager = new CallbackManager();
				}
			}
		}
		return mManager;
	}

	/**
	 * 注册一个监听
	 * @param fileInfo fileInfo
	 * @param listener 下载监听
     */
	public void addListener(DownloadFileInfo fileInfo, DownloadListener listener) {
		if (mCallbackMap.containsKey(fileInfo.getUrl())) {
			List<DownloadListener> list = mCallbackMap.get(fileInfo.getUrl());
			if (!list.contains(listener)) {
				list.add(listener);
			}
		} else {
			List<DownloadListener> list = new ArrayList<>();
			list.add(listener);
			mCallbackMap.put(fileInfo.getUrl(), list);
		}
		
		if (!mSpeedMap.containsKey(fileInfo.getUrl())) {
			mSpeedMap.put(fileInfo.getUrl(), new SpeedInfo());
		}
	}

	

	/**
	 * 回调初始化失败
	 */
	public void notifyInitFailure(DownloadFileInfo fileInfo, String errorInfo) {
		if (mInitCallbackMap.containsKey(fileInfo.getUrl())) {
			DownloadInitListener initListener = mInitCallbackMap.get(fileInfo.getUrl());
			if (initListener != null) {
				initListener.onInitFail(fileInfo,errorInfo);
			}
		}
	}

	/**
	 * 通知下载更新（下载中...）
	 */
	public synchronized void notifyRefresh(DownloadFileInfo fileInfo) {
		if (fileInfo == null) {
			return;
		}
		String url = fileInfo.getUrl();
		if (TextUtils.isEmpty(url)) {
			return;
		}
		if (mCallbackMap.containsKey(url)) {
			List<DownloadListener> listenerList = mCallbackMap.get(url);
			if (listenerList == null || listenerList.size() == 0) {
				return;
			}
			//计算下载速度
			if (mSpeedMap.containsKey(url)) {
				long currentTimeMillis = System.currentTimeMillis();
				long currentFinished = fileInfo.getFinished();
				SpeedInfo speedInfo = mSpeedMap.get(url);
				double realTimeSpeed = (currentFinished - speedInfo.getLastFinished()) * 1.0 / 
						((currentTimeMillis - speedInfo.getLastTimeMillis()) / 1000.0);
				fileInfo.setRealTimeSpeed(DownloadUtil.formatSpeed(realTimeSpeed));
				speedInfo.setLastFinished(currentFinished);
				speedInfo.setLastTimeMillis(currentTimeMillis);
			}
			//通知所有观察者更新进度
			for (int i = 0; i < listenerList.size(); i++) {
				fileInfo.setDownloadState(DownloadState.DOWNLOAD_ING);
				DownloadListener listener = listenerList.get(i);
				if (listener != null) {
					listener.onDownloading(fileInfo);
				}
			}
		}
	}

	/**
	 * 移动流量网络下载确认
	 * @param fileInfo fileInfo
     */
	public void notifyMobileNetConfirm(DownloadFileInfo fileInfo) {
		String url = fileInfo.getUrl();
		if (TextUtils.isEmpty(url)) {
			return;
		}
		if (mCallbackMap.containsKey(url)) {
			List<DownloadListener> listenerList = mCallbackMap.get(url);
			if (listenerList == null || listenerList.size() == 0) {
				return;
			}
			for (DownloadListener listener : listenerList) {
				if (listener != null) {
					listener.onMobileNetConfirm(fileInfo);
				}
			}
			
		}
	}


	/**
	 * 通知下载暂停
	 * @param fileInfo fileInfo
     */
	public void notifyPause(DownloadFileInfo fileInfo) {
		if (fileInfo == null) {
			return;
		}
		L.d( "notifyPause::fileInfo=" + fileInfo.toString());
		if (mSpeedMap.containsKey(fileInfo.getUrl())) {
			SpeedInfo speedInfo = mSpeedMap.get(fileInfo.getUrl());
			speedInfo.reset();
		}
		fileInfo.setDownloadState(DownloadState.DOWNLOAD_PAUSE);
		notifyChange(fileInfo, null);
	}

	/**
	 * 通知下载失败
	 */
	public synchronized void notifyFailure(DownloadFileInfo fileInfo, String errorInfo) {
		if (fileInfo == null) {
			return;
		}
		L.d( "notifyFailure::fileInfo=" + fileInfo.toString());
		if (mSpeedMap.containsKey(fileInfo.getUrl())) {
			SpeedInfo speedInfo = mSpeedMap.get(fileInfo.getUrl());
			speedInfo.reset();
		}
		fileInfo.setDownloadState(DownloadState.DOWNLOAD_FAILURE);
		notifyChange(fileInfo, errorInfo);
	}

	/**
	 * 通知下载完成
	 */
	public void notifyFinished(DownloadFileInfo fileInfo) {
		if (fileInfo == null) {
			return;
		}
		L.d("notifyFinished::fileInfo=" + fileInfo.toString());
		if (mSpeedMap.containsKey(fileInfo.getUrl())) {
			mSpeedMap.remove(fileInfo.getUrl());
		}
		fileInfo.setDownloadState(DownloadState.DOWNLOAD_SUCCESS);
		notifyChange(fileInfo, null);
	}

	/**
	 * 通知等待
	 */
	public synchronized void notifyWait(DownloadFileInfo fileInfo) {
		if (fileInfo == null) {
			return;
		}
		L.d("notifyWait::fileInfo=" + fileInfo.toString());
		if (mSpeedMap.containsKey(fileInfo.getUrl())) {
			SpeedInfo speedInfo = mSpeedMap.get(fileInfo.getUrl());
			speedInfo.reset();
		}
		fileInfo.setDownloadState(DownloadState.DOWNLOAD_WAIT);
		notifyChange(fileInfo, null);
	}

	/**
	 * 通用的回调出口
	 */
	private void notifyChange(DownloadFileInfo fileInfo, String errorInfo) {
		String url = fileInfo.getUrl();
		if (TextUtils.isEmpty(url)) {
			return;
		}
		if (mCallbackMap.containsKey(url)) {
			List<DownloadListener> listenerList = mCallbackMap.get(url);
			if (listenerList == null || listenerList.size() == 0) {
				return;
			}
			for (int i = 0; i < listenerList.size(); i++) {
				DownloadListener listener = listenerList.get(i);
				switch (fileInfo.getDownloadState()) {
				case DownloadState.DOWNLOAD_FAILURE:
					fileInfo.setRealTimeSpeed("");
					listener.onDownloadError(fileInfo, errorInfo);
					break;
//				case DownloadState.DOWNLOAD_ING:
//					fileInfo.setDownloadState(DownloadState.DOWNLOAD_ING);
//					listener.onDownloading(fileInfo);
//					break;
				case DownloadState.DOWNLOAD_PAUSE:
					fileInfo.setRealTimeSpeed("");
					listener.onDownloadPause(fileInfo);
					;
					break;
				case DownloadState.DOWNLOAD_SUCCESS:
					fileInfo.setRealTimeSpeed("");
					listener.onDownloadFinished(fileInfo);
					;
					break;
				case DownloadState.DOWNLOAD_WAIT:
					fileInfo.setRealTimeSpeed("");
					listener.onDownloadWait(fileInfo);
					break;

				default:
					break;
				}
			}
		}
	}

}
