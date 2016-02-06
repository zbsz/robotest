package org.robotest
package specs2

import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import android.content.ContentValues
import org.specs2._, matcher._, specification._

/**
 * Sample test using android database api.
 * - uses singe database for all tests
 * - creates test table before each test and drops it after
  */
class DatabaseRoboSpec extends RobolectricSpecification with MustMatchers with BeforeAfterEach with BeforeAfterAll {

  var helper: SQLiteOpenHelper = _
  var db: SQLiteDatabase = _

  def beforeAll(): Unit = {
    helper = new SQLiteOpenHelper(RuntimeEnvironment.application, "test", null, 1) {
      override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
      override def onCreate(db: SQLiteDatabase): Unit = {}
    }
  }

  def afterAll(): Unit = {
    RuntimeEnvironment.application.getDatabasePath(helper.getDatabaseName).delete()
  }

  def before = {
    db = helper.getWritableDatabase
    db.execSQL("CREATE TABLE Test(_id INTEGER PRIMARY KEY, value TEXT);")
  }

  def after = {
    db.execSQL("DROP TABLE IF EXISTS Test;")
    db.close()
  }

  def is = s2"""
  Robolectric test using database
  Insert row $insert
  Query all rows from empty table $query
  """

  def insert = {
    val values = new ContentValues()
    values.put("_id", Integer.valueOf(1))
    values.put("value", "test")

    db.insert("Test", null, values) must_== 1
  }

  def query = {
    val cursor = db.query("Test", null, null, null, null, null, null)
    cursor.moveToFirst() must beFalse and (cursor.getCount must_== 0)
  }
}
