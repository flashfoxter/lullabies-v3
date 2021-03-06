package com.ashomok.lullabies.di_dagger;


import com.ashomok.lullabies.ui.about_activity.AboutActivity;
import com.ashomok.lullabies.ui.about_activity.AboutModule;
import com.ashomok.lullabies.ui.full_screen_player_activity.FullScreenPlayerActivity;
import com.ashomok.lullabies.ui.full_screen_player_activity.FullScreenPlayerModule;
import com.ashomok.lullabies.ui.main_activity.MusicPlayerActivity;
import com.ashomok.lullabies.ui.main_activity.MusicPlayerModule;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;


/**
 * We want Dagger.Android to create a Subcomponent which has a parent Component of whichever module
 * ActivityBindingModule is on,
 * in our case that will be AppComponent. The beautiful part about this setup is that you never need
 * to tell AppComponent that it is going to have all these subcomponents
 * nor do you need to tell these subcomponents that AppComponent exists.
 * We are also telling Dagger.Android that this generated SubComponent needs to include the
 * specified modules and be aware of a scope annotation @ActivityScoped
 * When Dagger.Android annotation processor runs it will create subcomponents for us.
 */
@Module
public abstract class ActivityBindingModule {

    @ContributesAndroidInjector(modules = MusicPlayerModule.class)
    abstract MusicPlayerActivity musicPlayerActivity();

    @ContributesAndroidInjector(modules = AboutModule.class)
    abstract AboutActivity aboutActivity();

    @ContributesAndroidInjector(modules = FullScreenPlayerModule.class)
    abstract FullScreenPlayerActivity fullScreenPlayerActivity();
}
