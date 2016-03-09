package com.beyond.library.entity;

import com.beyond.library.util.ShortTextUtil;

import java.io.Serializable;

/**
 * 文件信息实体类
 * 
 * @author Beyond
 *
 */
public class DownloadFileInfo implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int id;
	private String url;
	private String fileName;
	private String showName;
	private long length;
	private long finished;
	private int progress;
	private int downloadState = DownloadState.DOWNLOAD_NOTSTART;
	private boolean initialized;
	private String realTimeSpeed;// 实时下载速度
	private String storagePath;// 下载存储路径
	private boolean acceptRanges;//是否支持断点下载

	public DownloadFileInfo() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String toString() {
		return "DownloadFileInfo [id=" + id + ", url=" + url + ", fileName=" + fileName + ", length=" + length + ", finished=" + finished + ", progress="
				+ progress + ", downloadState=" + downloadState + ", initialized=" + initialized + ", realTimeSpeed=" + realTimeSpeed + ", downloadPath="
				+ storagePath + "]";
	}

	public DownloadFileInfo(String url, String showName, String storagePath) {
		super();
		this.url = url;
		this.showName = showName;
		this.storagePath = storagePath;
		this.fileName = ShortTextUtil.generateFileNameByUrl(url);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public long getLength() {
		return length;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public long getFinished() {
		return finished;
	}

	public void setFinished(long finished) {
		this.finished = finished;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}

	public int getDownloadState() {
		return downloadState;
	}

	public void setDownloadState(int downloadState) {
		this.downloadState = downloadState;
	}

	public boolean hasInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public String getRealTimeSpeed() {
		return realTimeSpeed;
	}

	public void setRealTimeSpeed(String realTimeSpeed) {
		this.realTimeSpeed = realTimeSpeed;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public void setStoragePath(String storagePath) {
		this.storagePath = storagePath;
	}
	
	public boolean isAcceptRanges() {
		return acceptRanges;
	}
	
	public void setAcceptRanges(boolean acceptRanges) {
		this.acceptRanges = acceptRanges;
	}

	public String getShowName() {
		return showName;
	}

	public void setShowName(String showName) {
		this.showName = showName;
	}
}
