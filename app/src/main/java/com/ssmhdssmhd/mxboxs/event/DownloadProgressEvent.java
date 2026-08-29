package com.ssmhdssmhd.mxboxs.event;

/**
 * APK 下载进度事件（v5.7.11）。DownloadService 在后台下载，
 * 通过 EventBus 把进度 / 完成 / 失败事件发给 Updater 的对话框更新 UI。
 * 用户关对话框、切后台、Activity 销毁都不影响下载（Service 前台常驻）。
 */
public class DownloadProgressEvent {

    /** 状态：正在下载 */
    public static final int STATE_PROGRESS = 0;
    /** 状态：下载完成（准备安装） */
    public static final int STATE_SUCCESS = 1;
    /** 状态：下载失败 */
    public static final int STATE_ERROR = 2;
    /** 状态：扫描镜像中（probe 阶段） */
    public static final int STATE_PROBING = 3;
    /** 状态：已取消（用户或系统取消） */
    public static final int STATE_CANCELLED = 4;

    public final int state;
    /** 百分比 0-100，-1 表示未知 */
    public final int progress;
    /** 已下载字节 */
    public final long downloadedBytes;
    /** 总字节，-1 未知 */
    public final long totalBytes;
    /** 附加消息（错误原因 / 成功路径） */
    public final String message;

    public DownloadProgressEvent(int state, int progress, long downloadedBytes, long totalBytes, String message) {
        this.state = state;
        this.progress = progress;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.message = message;
    }

    public static DownloadProgressEvent probing(String msg) {
        return new DownloadProgressEvent(STATE_PROBING, 0, 0, -1, msg);
    }

    public static DownloadProgressEvent progress(int p, long d, long t) {
        return new DownloadProgressEvent(STATE_PROGRESS, p, d, t, null);
    }

    public static DownloadProgressEvent success(String path) {
        return new DownloadProgressEvent(STATE_SUCCESS, 100, 0, 0, path);
    }

    public static DownloadProgressEvent error(String msg) {
        return new DownloadProgressEvent(STATE_ERROR, -1, 0, 0, msg);
    }

    public static DownloadProgressEvent cancelled() {
        return new DownloadProgressEvent(STATE_CANCELLED, -1, 0, 0, null);
    }
}
