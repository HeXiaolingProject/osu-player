package pw.lictex.osuplayer;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

import androidx.preference.*;

import java.io.*;
import java.util.*;

import lombok.*;
import pw.lictex.osuplayer.activity.*;
import pw.lictex.osuplayer.audio.*;

public class PlayerService extends Service {
    private final int ID = 141;
    private AudioManager audioManager;
    @Getter private ArrayList<String> allMapList = new ArrayList<>();
    @Getter private ArrayList<String> collectionMapList = new ArrayList<>();
    @Getter @Setter private boolean playCollectionList = false;
    @Getter private String currentPath = null;
    @Getter private OsuAudioPlayer osuAudioPlayer;
    @Getter @Setter private Runnable onUpdateCallback;
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                audioManager.unregisterMediaButtonEventReceiver(new ComponentName(PlayerService.this.getPackageName(), MediaBroadcastReceiver.class.getName()));
                audioManager.registerMediaButtonEventReceiver(new ComponentName(PlayerService.this.getPackageName(), MediaBroadcastReceiver.class.getName()));
                resume();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                audioManager.unregisterMediaButtonEventReceiver(new ComponentName(PlayerService.this.getPackageName(), MediaBroadcastReceiver.class.getName()));
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                pause();
                break;
        }
    };
    @Getter @Setter private LoopMode loopMode = LoopMode.All;

    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerServiceBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        osuAudioPlayer = new OsuAudioPlayer(getApplicationContext());
        osuAudioPlayer.setOnBeatmapEndCallback(() -> {
            switch (loopMode) {
                case Single:
                    play(getPlaylist().indexOf(currentPath));
                    break;
                case All:
                    play(getPlaylist().indexOf(currentPath) + 1);
                    break;
                case Random:
                    play(((int) (Math.random() * getPlaylist().size())));
            }

        });
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ensureAudioFocus();
        startForeground(ID, getNotification());
    }

    private void ensureAudioFocus() {
        audioManager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaBroadcastReceiver.class.getName()));
        audioManager.abandonAudioFocus(focusChangeListener);
        audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        audioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaBroadcastReceiver.class.getName()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        osuAudioPlayer.destroy();
        osuAudioPlayer = null;
        audioManager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaBroadcastReceiver.class.getName()));
        audioManager.abandonAudioFocus(focusChangeListener);
    }

    public void next() {
        play(getPlaylist().indexOf(currentPath) + 1);
    }

    public void previous() {
        play(getPlaylist().indexOf(currentPath) - 1);
    }

    public synchronized void play(int index) {
        ensureAudioFocus();
        if (getPlaylist().size() != 0) {
            index = (index < 0 || index >= getPlaylist().size()) ? 0 : index;
            currentPath = getPlaylist().get(index);
            osuAudioPlayer.openBeatmapSet(new File(currentPath).getParent() + "/");
            osuAudioPlayer.playBeatmap(new File(currentPath).getName());
            if (onUpdateCallback != null) onUpdateCallback.run();
        }
        rebuildNotification();
    }

    public void resume() {
        ensureAudioFocus();
        osuAudioPlayer.play();
        if (onUpdateCallback != null) onUpdateCallback.run();
    }

    public void pause() {
        osuAudioPlayer.pause();
        if (onUpdateCallback != null) onUpdateCallback.run();
    }

    public void rebuildNotification() {
        var notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(ID, getNotification());
    }

    public List<String> getPlaylist() {
        return playCollectionList ? collectionMapList : allMapList;
    }

    private Notification getNotification() {
        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        var builder = new Notification.Builder(getApplication()).setAutoCancel(true).setSmallIcon(R.drawable.ic_osu_96)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId("Service");

        if (allMapList.size() == 0 || osuAudioPlayer.getTitle() == null) builder.setContentTitle("闲置中");
        else if (sharedPreferences.getBoolean("use_unicode_metadata", false))
            builder.setContentTitle(osuAudioPlayer.getTitle() + " - " + osuAudioPlayer.getArtist()).
                    setContentText(getString(R.string.version_by_mapper, osuAudioPlayer.getVersion(), osuAudioPlayer.getMapper()));
        else builder.setContentTitle(osuAudioPlayer.getRomanisedTitle() + " - " + osuAudioPlayer.getRomanisedArtist()).
                    setContentText(getString(R.string.version_by_mapper, osuAudioPlayer.getVersion(), osuAudioPlayer.getMapper()));

        return builder.build();
    }

    public enum LoopMode {
        Single, All, Random
    }

    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

}
