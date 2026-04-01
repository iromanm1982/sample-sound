package org.role.samples_button.core.database

import androidx.room.RoomDatabase
import javax.`annotation`.processing.Generated
import kotlin.Suppress
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SoundButtonDao_Impl(
  __db: RoomDatabase,
) : SoundButtonDao {
  private val __db: RoomDatabase
  init {
    this.__db = __db
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
