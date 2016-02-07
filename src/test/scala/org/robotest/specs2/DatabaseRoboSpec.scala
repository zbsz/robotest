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
trait DatabaseRoboSpec
extends RobolectricSpecification
with MustMatchers
with BeforeAll
{
  lazy val helper: SQLiteOpenHelper = new SQLiteOpenHelper(RuntimeEnvironment.application, "test", null, 1) {
      override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
      override def onCreate(db: SQLiteDatabase): Unit = {}
    }

  lazy val db: SQLiteDatabase = helper.getWritableDatabase

  def beforeAll(): Unit = {
    db.execSQL("CREATE TABLE Test(_id INTEGER PRIMARY KEY, value TEXT);")
  }

  override def afterAll(): Unit = {
    db.execSQL("DROP TABLE IF EXISTS Test;")
    db.close()
    RuntimeEnvironment.application.getDatabasePath(helper.getDatabaseName).delete()
    super.afterAll()
  }
}

class DatabaseInsertRoboSpec
extends DatabaseRoboSpec
{
  def is = s2"""
  Robolectric test using database

  Insert row $insert
  """

  def insert = {
    val values = new ContentValues()
    values.put("_id", Integer.valueOf(1))
    values.put("value", "test")

    db.insert("Test", null, values) must_== 1
  }
}

class DatabaseQueryRoboSpec
extends DatabaseRoboSpec
{
  def is = s2"""
  Robolectric test using database

  Query all rows from empty table $query
  """

  def query = {
    val cursor = db.query("Test", null, null, null, null, null, null)
    cursor.moveToFirst() must beFalse and (cursor.getCount must_== 0)
  }
}
