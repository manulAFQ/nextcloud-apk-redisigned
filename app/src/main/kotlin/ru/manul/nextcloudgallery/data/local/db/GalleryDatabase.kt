package ru.manul.nextcloudgallery.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        MediaEntity::class,
        SyncCursorEntity::class,
        UploadJobEntity::class,
        AutoUploadPolicyEntity::class,
        AutoUploadItemEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun mediaDao(): MediaDao

    abstract fun syncCursorDao(): SyncCursorDao

    abstract fun uploadJobDao(): UploadJobDao

    abstract fun autoUploadDao(): AutoUploadDao

    companion object {
        const val DEFAULT_NAME = "nextcloud-gallery.db"

        fun create(
            context: Context,
            name: String = DEFAULT_NAME,
        ): GalleryDatabase = Room.databaseBuilder(
            context.applicationContext,
            GalleryDatabase::class.java,
            name,
        )
            .addMigrations(*GalleryDatabaseMigrations.ALL)
            .build()
    }
}
