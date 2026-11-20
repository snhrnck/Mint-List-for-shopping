package com.snhrnck.mintlist

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ShoppingList(
    val id: Long = 0,
    val title: String,
    val lastModified: Long = System.currentTimeMillis(),
    val isClosed: Boolean = false
)

data class ShoppingItem(
    val id: Long = 0,
    val listId: Long,
    val text: String,
    val isChecked: Boolean = false
)

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "planner.db", null, 1) {

    private val refreshTrigger = MutableStateFlow(0L)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                lastModified INTEGER NOT NULL,
                isClosed INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listId INTEGER NOT NULL,
                text TEXT NOT NULL,
                isChecked INTEGER NOT NULL,
                FOREIGN KEY(listId) REFERENCES lists(id) ON DELETE CASCADE
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    private fun notifyChange() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    fun getActiveList(): Flow<ShoppingList?> = refreshTrigger.map {
        withContext(Dispatchers.IO) {
            readableDatabase.rawQuery(
                "SELECT id, title, lastModified, isClosed FROM lists WHERE isClosed = 0 ORDER BY id DESC LIMIT 1",
                null
            ).use { c ->
                if (c.moveToFirst()) {
                    ShoppingList(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1)
                } else null
            }
        }
    }

    fun getHistoryLists(): Flow<List<ShoppingList>> = refreshTrigger.map {
        withContext(Dispatchers.IO) {
            val result = mutableListOf<ShoppingList>()
            readableDatabase.rawQuery(
                "SELECT id, title, lastModified, isClosed FROM lists WHERE isClosed = 1 ORDER BY lastModified DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    result.add(ShoppingList(c.getLong(0), c.getString(1), c.getLong(2), true))
                }
            }
            result
        }
    }

    fun getItemsForList(listId: Long): Flow<List<ShoppingItem>> = refreshTrigger.map {
        withContext(Dispatchers.IO) {
            val result = mutableListOf<ShoppingItem>()
            readableDatabase.rawQuery(
                "SELECT id, listId, text, isChecked FROM items WHERE listId = ? ORDER BY id ASC",
                arrayOf(listId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    result.add(ShoppingItem(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3) == 1))
                }
            }
            result
        }
    }

    suspend fun insertList(list: ShoppingList): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("title", list.title)
            put("lastModified", list.lastModified)
            put("isClosed", if (list.isClosed) 1 else 0)
        }
        val id = writableDatabase.insert("lists", null, cv)
        notifyChange()
        id
    }

    suspend fun updateList(list: ShoppingList) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("title", list.title)
            put("lastModified", list.lastModified)
            put("isClosed", if (list.isClosed) 1 else 0)
        }
        writableDatabase.update("lists", cv, "id = ?", arrayOf(list.id.toString()))
        notifyChange()
    }

    suspend fun insertItem(item: ShoppingItem) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("listId", item.listId)
            put("text", item.text)
            put("isChecked", if (item.isChecked) 1 else 0)
        }
        writableDatabase.insert("items", null, cv)
        notifyChange()
    }

    suspend fun updateItem(item: ShoppingItem) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("text", item.text)
            put("isChecked", if (item.isChecked) 1 else 0)
        }
        writableDatabase.update("items", cv, "id = ?", arrayOf(item.id.toString()))
        notifyChange()
    }

    suspend fun deleteItem(item: ShoppingItem) = withContext(Dispatchers.IO) {
        writableDatabase.delete("items", "id = ?", arrayOf(item.id.toString()))
        notifyChange()
    }

    suspend fun deleteList(list: ShoppingList) = withContext(Dispatchers.IO) {
        writableDatabase.delete("lists", "id = ?", arrayOf(list.id.toString()))
        notifyChange()
    }

    suspend fun cloneList(oldList: ShoppingList) = withContext(Dispatchers.IO) {
        val newListId = insertList(ShoppingList(title = "${oldList.title} (Copy)", isClosed = false))
        val items = mutableListOf<ShoppingItem>()

        readableDatabase.rawQuery("SELECT text FROM items WHERE listId = ?", arrayOf(oldList.id.toString())).use { c ->
            while (c.moveToNext()) {
                items.add(ShoppingItem(listId = newListId, text = c.getString(0)))
            }
        }
        items.forEach { insertItem(it) }
        notifyChange()
    }

    suspend fun clearHistoryAndVacuum() = withContext(Dispatchers.IO) {
        writableDatabase.delete("lists", "isClosed = 1", null)
        writableDatabase.execSQL("VACUUM")
        notifyChange()
    }

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            AppDatabase(context).also { INSTANCE = it }
        }
    }
}