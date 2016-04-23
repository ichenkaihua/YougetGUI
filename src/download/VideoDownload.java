package download;

import executor.Executor;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import sample.DownloadData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownload extends Executor {

    public VideoDownload() {
        super(VideoDownload.class, "you-get-0.4.365-win32.exe");
        new ProgressChecker().start();
    }

    public void save(DownloadData downloadData) {
        this.downloadData = downloadData;

        ChangeListener<String> listener = new ChangeListener<String>() {

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                {
                    Matcher matcher = NAME_REGEX.matcher(newValue);
                    if (matcher.matches()) {
                        updateNameOnUiThread(matcher.group("name").trim());
                    }
                }

                {
                    Matcher matcher = MERGING_REGEX.matcher(newValue);
                    if (matcher.matches()) {
                        updateProgressOnUiThread("合并文件中");
                    }
                }

                for (String split : newValue.split(" ")) {
                    Matcher matcher = PROGRESS_REGEX.matcher(split);
                    if (matcher.matches()) {
                        downloadedSize.set(Double.parseDouble(matcher.group("downloaded")));
                        totalSize.set(Double.parseDouble(matcher.group("total")));
                        updateProgressOnUiThread("" + downloadedSize.get() + "/" + totalSize.get() + " MB");
                    }
                }
            }
        };
        statusProperty().addListener(listener);
        isDownloading.set(true);

        while (isFirstRun || shouldRestartDownload) {
            isFirstRun = false;
            shouldRestartDownload = false;
            updateProgressOnUiThread("开始下载");
            execute(new VideoDownloadParameters(downloadData.getSaveDir(), downloadData.getUrl()), false);
            updateProgressOnUiThread("下载完成");
        }

        isDownloading.set(false);
        statusProperty().removeListener(listener);
    }

    private void updateNameOnUiThread(String name) {
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                downloadData.setName(name);
            }

        });
    }

    private void updateProgressOnUiThread(String progress) {
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                downloadData.setProgress(progress);
            }

        });
    }

    private DownloadData downloadData;

    private boolean isFirstRun = true;

    private boolean shouldRestartDownload = false;

    public BooleanProperty isDownloadingProperty() {
        return isDownloading;
    }

    private BooleanProperty isDownloading = new SimpleBooleanProperty();

    private DoubleProperty downloadedSize = new SimpleDoubleProperty();

    private DoubleProperty totalSize = new SimpleDoubleProperty();

    private static final Pattern PROGRESS_REGEX = Pattern.compile("\\(?(?<downloaded>[\\d\\.]+)/(?<total>[\\d\\.]+)MB\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_REGEX = Pattern.compile("title:(?<name>.+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MERGING_REGEX = Pattern.compile("Merging video parts.*", Pattern.CASE_INSENSITIVE);

    private class ProgressChecker extends Thread {

        public ProgressChecker() {
            setDaemon(true);
        }

        private final long CHECK_INTERVAL = 10;

        // MB/s
        private final double MIN_DOWNLOAD_SPEED = 0.1;

        private final double MIN_DOWNLOAD_SIZE = CHECK_INTERVAL * MIN_DOWNLOAD_SPEED;

        private double lastDownloadedData;

        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(CHECK_INTERVAL * 1000);

                    if (!isDownloading.get()) {
                        continue;
                    }

                    // if video size not get yet
                    if (totalSize.get() < 1) {
                        continue;
                    }

                    // if video is merging
                    if (totalSize.get() - downloadedSize.get() < 1) {
                        continue;
                    }

                    System.out.println(lastDownloadedData + ", " + downloadedSize.get() + "/" + totalSize.get());

                    if (lastDownloadedData > downloadedSize.get()) {
                        lastDownloadedData = downloadedSize.get();
                        continue;
                    }

                    if (downloadedSize.get() - lastDownloadedData < MIN_DOWNLOAD_SIZE) {
                        updateProgressOnUiThread("重启下载中");
                        shouldRestartDownload = true;
                        forceCancel();
                    }

                    lastDownloadedData = downloadedSize.get();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

}