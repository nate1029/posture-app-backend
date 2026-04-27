package com.example.neckguard.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {PostureLog.class}, version = 1, exportSchema = false)
public abstract class NeckGuardDatabase extends RoomDatabase {
    public abstract PostureLogDao postureLogDao();

    private static volatile NeckGuardDatabase INSTANCE;

    public static NeckGuardDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (NeckGuardDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            NeckGuardDatabase.class, "neckguard_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
