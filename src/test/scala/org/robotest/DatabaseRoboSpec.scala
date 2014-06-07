package org.robotest

import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import org.robolectric.Robolectric
import org.scalatest._
import android.content.ContentValues

/**
 * Sample test using android database api.
 * - uses singe database for all tests
 * - creates test table before each test and drops it after
  */
class DatabaseRoboSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricSuite {

  var helper: SQLiteOpenHelper = _
  var db: SQLiteDatabase = _

  override protected def beforeAll(): Unit = {
    Robolectric.application should not be null

    helper = new SQLiteOpenHelper(Robolectric.application, "test", null, 1) {
      override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
      override def onCreate(db: SQLiteDatabase): Unit = {}
    }
  }

  override protected def afterAll(): Unit = {
    Robolectric.application.getDatabasePath(helper.getDatabaseName).delete()
  }

  before {
    db = helper.getWritableDatabase
    db.execSQL("CREATE TABLE Test(_id INTEGER PRIMARY KEY, value TEXT);")
  }

  after {
    db.execSQL("DROP TABLE IF EXISTS Test;")
    db.close()
  }

  feature("Robolectric test using database") {

    scenario("Insert row") {
      val values = new ContentValues()
      values.put("_id", Integer.valueOf(1))
      values.put("value", "test")

      db.insert("Test", null, values) shouldEqual 1
    }

    scenario("Query all rows from empty table") {

      val cursor = db.query("Test", null, null, null, null, null, null)
      cursor.moveToFirst() shouldEqual false
      cursor.getCount shouldEqual 0
    }
  }

}
