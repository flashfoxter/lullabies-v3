package com.ashomok.lullabies.ui.full_screen_player_activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.session.MediaControllerCompat;

import androidx.annotation.Nullable;

import com.ashomok.lullabies.ui.main_activity.MusicPlayerPresenter;
import com.ashomok.lullabies.utils.LogHelper;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;

public class FullScreenPlayerPresenter implements FullScreenPlayerContract.Presenter {
    public static final String TAG = LogHelper.makeLogTag(MusicPlayerPresenter.class);

    @Nullable
    public FullScreenPlayerContract.View view;

    private Context context;


    /**
     * Dagger strictly enforces that arguments not marked with {@code @Nullable} are not injected
     * with {@code @Nullable} values.
     */
    @Inject
    FullScreenPlayerPresenter(Context context) {
        this.context = context;
    }

    @Override
    public void takeView(FullScreenPlayerContract.View activity) {
        view = activity;
        init();
    }

    private void init() { }

    @Override
    public void dropView() {
        view = null;
    }

}
