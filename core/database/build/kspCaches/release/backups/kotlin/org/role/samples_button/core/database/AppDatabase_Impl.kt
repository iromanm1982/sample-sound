package org.role.samples_button.core.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _groupDao: Lazy<GroupDao> = lazy {
    GroupDao_Impl(this)
  }

  private val _soundButtonDao: Lazy<SoundButtonDao> = lazy {
    SoundButtonDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "e158954bc9e7cabbd6e5751d54504559", "8003252032ed274c24b5d15117d30e6e") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sound_buttons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `filePath` TEXT NOT NULL, `groupId` INTEGER NOT NULL, `position` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e158954bc9e7cabbd6e5751d54504559')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `groups`")
        connection.execSQL("DROP TABLE IF EXISTS `sound_buttons`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsGroups: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsGroups.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGroups.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsGroups.put("position", TableInfo.Column("position", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysGroups: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesGroups: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoGroups: TableInfo = TableInfo("groups", _columnsGroups, _foreignKeysGroups,
            _indicesGroups)
        val _existingGroups: TableInfo = read(connection, "groups")
        if (!_infoGroups.equals(_existingGroups)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |groups(org.role.samples_button.core.database.GroupEntity).
              | Expected:
              |""".trimMargin() + _infoGroups + """
              |
              | Found:
              |""".trimMargin() + _existingGroups)
        }
        val _columnsSoundButtons: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSoundButtons.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSoundButtons.put("label", TableInfo.Column("label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSoundButtons.put("filePath", TableInfo.Column("filePath", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSoundButtons.put("groupId", TableInfo.Column("groupId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSoundButtons.put("position", TableInfo.Column("position", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSoundButtons: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSoundButtons: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSoundButtons: TableInfo = TableInfo("sound_buttons", _columnsSoundButtons,
            _foreignKeysSoundButtons, _indicesSoundButtons)
        val _existingSoundButtons: TableInfo = read(connection, "sound_buttons")
        if (!_infoSoundButtons.equals(_existingSoundButtons)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sound_buttons(org.role.samples_button.core.database.SoundButtonEntity).
              | Expected:
              |""".trimMargin() + _infoSoundButtons + """
              |
              | Found:
              |""".trimMargin() + _existingSoundButtons)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "groups", "sound_buttons")
  }

  public override fun clearAllTables() {
    super.performClear(false, "groups", "sound_buttons")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(GroupDao::class, GroupDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SoundButtonDao::class, SoundButtonDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun groupDao(): GroupDao = _groupDao.value

  public override fun soundButtonDao(): SoundButtonDao = _soundButtonDao.value
}
