package com.ssmhdssmhd.mxboxs.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.bean.Config;
import com.ssmhdssmhd.mxboxs.bean.Device;
import com.ssmhdssmhd.mxboxs.bean.History;
import com.ssmhdssmhd.mxboxs.bean.Keep;
import com.ssmhdssmhd.mxboxs.bean.Live;
import com.ssmhdssmhd.mxboxs.bean.Site;
import com.ssmhdssmhd.mxboxs.bean.Track;
import com.ssmhdssmhd.mxboxs.db.dao.ConfigDao;
import com.ssmhdssmhd.mxboxs.db.dao.DeviceDao;
import com.ssmhdssmhd.mxboxs.db.dao.HistoryDao;
import com.ssmhdssmhd.mxboxs.db.dao.KeepDao;
import com.ssmhdssmhd.mxboxs.db.dao.LiveDao;
import com.ssmhdssmhd.mxboxs.db.dao.SiteDao;
import com.ssmhdssmhd.mxboxs.db.dao.TrackDao;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 35;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
