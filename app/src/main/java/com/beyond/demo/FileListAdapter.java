package com.beyond.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.beyond.library.entity.DownloadFileInfo;
import com.beyond.library.entity.DownloadState;
import com.beyond.library.listener.DownloadInitListener;
import com.beyond.library.listener.DownloadListener;
import com.beyond.library.manager.DownloadManager;
import com.beyond.library.util.DownloadUtil;
import com.beyond.library.util.L;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FileListAdapter extends BaseAdapter {

	protected static final int ERROR = 10;
	private static final int NORMAL = 11;
	protected static final int MOBILE_NET_CONFIRM = 12;
	private Context mContext;
	private List<DownloadFileInfo> mFileList;
	private LayoutInflater inflater;
	private DownloadManager mDownloadManager;
	private Map<Integer,DownloadListener> mListenerMap;

	public FileListAdapter(Context context, List<DownloadFileInfo> fileInfos) {
		this.mContext = context;
		this.mFileList = fileInfos;
		this.inflater = LayoutInflater.from(context);
		mDownloadManager = DownloadManager.getInstance();
		mListenerMap = new HashMap<>();
	}

	@Override
	public int getCount() {
		return mFileList.size();
	}

	@Override
	public Object getItem(int position) {
		return mFileList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = inflater.inflate(R.layout.download_list_item_for_test, null);
			holder.tvFileName = (TextView) convertView.findViewById(R.id.tv_filename);
			holder.tvProgress = (TextView) convertView.findViewById(R.id.tv_progress);
			holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progressBar);
			holder.btnStart = (Button) convertView.findViewById(R.id.btn_start);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		
		final DownloadFileInfo fileInfo = mFileList.get(position);
		// 初始化进度
		if (!fileInfo.hasInitialized()) {
			mDownloadManager.init(mContext, fileInfo, new DownloadInitListener() {
				
				@Override
				public void onInitSuccess(DownloadFileInfo fileInfo) {
					Message msg = Message.obtain();
					msg.what = DownloadListenerImpl.NORMAL;
					msg.obj = fileInfo;
					mHandler.sendMessage(msg);
				}

				@Override
				public void onInitFail(DownloadFileInfo fileInfo, String errorInfo) {
					if (!TextUtils.isEmpty(errorInfo)) {
						Toast.makeText(mContext, errorInfo, Toast.LENGTH_LONG).show();
					}
				}
			});
		}
		
		convertView.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(mContext,DownloadDetailActivty.class);
				intent.putExtra("DownloadFileInfo", fileInfo);
				mContext.startActivity(intent);
			}
		});
		holder.tvFileName.setText(fileInfo.getShowName());
		holder.progressBar.setMax(100);
		
		holder.progressBar.setProgress(fileInfo.getProgress());
		holder.tvProgress.setText(fileInfo.getRealTimeSpeed());
		//显示下载状态
		String stateDesc = DownloadUtil.getDownloadStateDesc(fileInfo);
		holder.btnStart.setText(stateDesc);

		mDownloadManager.registListener(fileInfo, getListenerByPosition(position));

		holder.btnStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				switch (fileInfo.getDownloadState()) {
				case DownloadState.DOWNLOAD_NOTSTART:// 未下载——>下载
					//注册监听
					mDownloadManager.executeDownload(mContext, fileInfo);
					break;
				case DownloadState.DOWNLOAD_ING:// 下载中——>暂停
					mDownloadManager.executePause(mContext, fileInfo);
					break;
				case DownloadState.DOWNLOAD_PAUSE:// 暂停——>继续下载
					//注册监听
					mDownloadManager.executeResume(mContext, fileInfo);
					break;
				case DownloadState.DOWNLOAD_SUCCESS:// 下载完成——>“安装”
					DownloadUtil.installApk(mContext,fileInfo);
					break;
				case DownloadState.DOWNLOAD_FAILURE:// 下载失败——>重新下载
					mDownloadManager.executeDownload(mContext, fileInfo);
					break;

				default:
					break;
				}

			}
		});

		return convertView;
	}

	/**
	 * 每一个下载的position都对应一个downloadListener
	 * @param pos
	 * @return
     */
	private DownloadListener getListenerByPosition(int pos){
		DownloadListener listener = mListenerMap.get(pos);
		if (listener == null) {
			listener = new DownloadListenerImpl(mHandler,mContext);
			mListenerMap.put(pos,listener);
		}
		return listener;
	}



	public Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case DownloadListenerImpl.NORMAL:
				DownloadFileInfo fileInfo = (DownloadFileInfo) msg.obj;
				if (fileInfo == null) return;
				
				for (int i = 0; i < mFileList.size(); i++) {
					DownloadFileInfo downloadFileInfo = mFileList.get(i);
					if (downloadFileInfo.getUrl().equals(fileInfo.getUrl())) {
						downloadFileInfo.setDownloadState(fileInfo.getDownloadState());
						downloadFileInfo.setFinished(fileInfo.getFinished());
						downloadFileInfo.setProgress(fileInfo.getProgress());
						downloadFileInfo.setRealTimeSpeed(fileInfo.getRealTimeSpeed());
						downloadFileInfo.setInitialized(fileInfo.hasInitialized());
						break;
					}
				}
				notifyDataSetChanged();
				break;
			case DownloadListenerImpl.ERROR:
				notifyDataSetChanged();
				break;
			case DownloadListenerImpl.MOBILE_NET_CONFIRM:
				L.e("运营商流量下载确认");
				final DownloadFileInfo fileInfo2 = (DownloadFileInfo) msg.obj;
				String sizeStr = DownloadUtil.formatFileSize(fileInfo2.getLength(), false);
				String content = "您现在使用的是运营商流量，下载文件大小为："+sizeStr+",确认下载？";

			default:
				break;
			}
		}
	};

	
	static class ViewHolder {
		TextView tvProgress;
		TextView tvFileName;
		ProgressBar progressBar;
		Button btnStart;
	}

}
