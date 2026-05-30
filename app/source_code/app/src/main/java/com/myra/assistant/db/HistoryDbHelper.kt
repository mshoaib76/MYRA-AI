package com.myra.assistant.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
data class CommandHistory(
    val id: Long,
    val commandText: String,
    val timestamp: Long
)

class HistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "MyraHistory.db"
        const val TABLE_NAME = "command_history"
        const val COLUMN_ID = "id"
        const val COLUMN_TEXT = "command_text"
        const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_TEXT TEXT," +
                "$COLUMN_TIMESTAMP INTEGER" +
                ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertCommand(commandText: String, timestamp: Long = System.currentTimeMillis()) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TEXT, commandText)
            put(COLUMN_TIMESTAMP, timestamp)
        }
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun getAllCommands(): List<CommandHistory> {
        val commands = mutableListOf<CommandHistory>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                commands.add(CommandHistory(id, text, timestamp))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return commands
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}
